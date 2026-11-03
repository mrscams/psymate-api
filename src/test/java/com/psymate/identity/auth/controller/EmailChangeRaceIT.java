package com.psymate.identity.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.psymate.identity.auth.AbstractLoginIT;
import com.psymate.identity.auth.RegistrationFixtures;
import com.psymate.identity.auth.repository.PractitionerRepository;
import com.psymate.testsupport.MailpitClient;
import com.psymate.testsupport.SharedMailpit;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M10-04 : course d'unicité à la confirmation. Un autre compte s'inscrit (transaction séparée,
 * validée) avec la nouvelle adresse entre la vérification d'unicité et l'écriture : la contrainte
 * ux_practitioner_email lève au flush, la transaction est annulée et le lien donne 410.
 */
class EmailChangeRaceIT extends AbstractLoginIT {

  @MockitoSpyBean private PractitionerRepository practitioners;

  @Test
  void anAddressTakenAfterTheCheckGives410_andLeavesTokenAndEmailUntouched() throws Exception {
    String oldEmail = account();
    UUID id = idOf(oldEmail);
    String newAddress = newEmail();
    post(
        "/api/auth/email/change",
        Map.of("newEmail", newAddress, "password", PASSWORD),
        session(oldEmail),
        IP);
    String text =
        new MailpitClient(SharedMailpit.container())
            .lastText(newAddress, "confirmez votre nouvelle");
    Matcher m = Pattern.compile("/confirmer-email/([A-Za-z0-9_-]+)").matcher(text);
    assertThat(m.find()).isTrue();
    String token = m.group(1);

    // Après la vérification d'unicité de la confirmation, un autre compte prend l'adresse.
    AtomicBoolean armed = new AtomicBoolean(true);
    doAnswer(
            invocation -> {
              Object result = invocation.callRealMethod();
              if (newAddress.equals(invocation.getArgument(0))
                  && armed.compareAndSet(true, false)) {
                // Autre fil : transaction indépendante, validée avant la reprise de la
                // confirmation.
                CompletableFuture.runAsync(() -> registerOn(newAddress)).join();
              }
              return result;
            })
        .when(practitioners)
        .findByEmailNormalized(anyString());

    MvcResult result = post("/api/auth/email/confirm", Map.of("token", token), null, IP);

    assertThat(armed).as("la course a bien été jouée").isFalse();
    assertThat(result.getResponse().getStatus()).isEqualTo(410);
    assertThat(body(result).path("code").asString()).isEqualTo("TOKEN_INVALID_OR_EXPIRED");
    assertThat(jdbc.queryForObject("select email from practitioner where id = ?", String.class, id))
        .isEqualTo(oldEmail);
    // L'autre compte détient bien l'adresse.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from practitioner where email_normalized = ?",
                Integer.class,
                newAddress))
        .isEqualTo(1);
    // Le jeton n'est pas consommé (rollback), aucun email_changed.
    assertThat(
            jdbc.queryForObject(
                "select consumed_at from auth_token where practitioner_id = ? and kind ="
                    + " 'email_change'",
                Timestamp.class,
                id))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_event where practitioner_id = ? and kind ="
                    + " 'email_changed'",
                Integer.class,
                id))
        .isZero();
  }

  private void registerOn(String email) {
    try {
      MvcResult r =
          post(
              "/api/auth/register",
              RegistrationFixtures.payload(email, PASSWORD, "psychologist"),
              null,
              IP);
      assertThat(r.getResponse().getStatus()).isEqualTo(201);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
