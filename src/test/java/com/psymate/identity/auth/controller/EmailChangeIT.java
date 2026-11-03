package com.psymate.identity.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.psymate.identity.auth.AbstractLoginIT;
import com.psymate.identity.auth.RegistrationFixtures;
import com.psymate.testsupport.MailpitClient;
import com.psymate.testsupport.SharedMailpit;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * M10-04 : changement d'e-mail d'un compte confirmé (POST /api/auth/email/change avec une session,
 * puis POST /api/auth/email/confirm). Chaque test crée ses comptes (adresses uniques) et ne regarde
 * que ses données et ses destinataires (base et Mailpit partagés).
 */
class EmailChangeIT extends AbstractLoginIT {

  private static final String CHANGE = "/api/auth/email/change";
  private static final String CONFIRM = "/api/auth/email/confirm";
  private static final String LINK_SUBJECT = "confirmez votre nouvelle adresse";
  private static final Pattern LINK = Pattern.compile("/confirmer-email/([A-Za-z0-9_-]+)");

  private MailpitClient mails;

  @BeforeEach
  void mailbox() {
    mails = new MailpitClient(SharedMailpit.container());
  }

  // ---- outils

  private static String uniqueIp() {
    return "198.51.100." + (1 + (int) (Math.random() * 250));
  }

  private MvcResult change(Cookie session, String newAddress, String password, String ip)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("newEmail", newAddress);
    body.put("password", password);
    return post(CHANGE, body, session, ip);
  }

  private MvcResult change(Cookie session, String newAddress) throws Exception {
    return change(session, newAddress, PASSWORD, IP);
  }

  private int confirm(String token, Cookie session) throws Exception {
    return post(CONFIRM, Map.of("token", token), session, IP).getResponse().getStatus();
  }

  private String linkToken(String newAddress) throws Exception {
    String text = mails.lastText(newAddress, LINK_SUBJECT);
    assertThat(text).as("e-mail de confirmation envoyé à " + newAddress).isNotNull();
    Matcher m = LINK.matcher(text);
    assertThat(m.find()).isTrue();
    return m.group(1);
  }

  private int sessionStatus(Cookie cookie) throws Exception {
    return mvc.perform(get("/api/auth/session").cookie(cookie))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private int tokens(String email, String kind) {
    return jdbc.queryForObject(
        "select count(*) from auth_token t join practitioner p on p.id = t.practitioner_id"
            + " where p.email_normalized = ? and t.kind = ?",
        Integer.class,
        email,
        kind);
  }

  private String currentEmail(UUID id) {
    return jdbc.queryForObject("select email from practitioner where id = ?", String.class, id);
  }

  private int loginStatus(String email) throws Exception {
    return login(email, PASSWORD, null, IP).getResponse().getStatus();
  }

  // ---- parcours nominal

  @Test
  void confirmedAccountChangesItsEmail_oldAddressValidUntilConfirmation() throws Exception {
    String oldEmail = account("approved");
    UUID id = idOf(oldEmail);
    String newAddress = newEmail();
    Cookie current = session(oldEmail);
    Cookie other = session(oldEmail);

    MvcResult requested = change(current, newAddress);

    assertThat(requested.getResponse().getStatus()).isEqualTo(202);
    assertThat(requested.getResponse().getContentAsString()).isEmpty();
    String token = linkToken(newAddress);
    // L'ancienne adresse reste la seule valable tant que le lien n'est pas suivi.
    assertThat(currentEmail(id)).isEqualTo(oldEmail);
    assertThat(loginStatus(oldEmail)).isEqualTo(200);
    assertThat(loginStatus(newAddress)).isEqualTo(401);
    assertThat(tokens(oldEmail, "email_change")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select payload ->> 'newEmail' from auth_token where practitioner_id = ? and"
                    + " kind = 'email_change'",
                String.class,
                id))
        .isEqualTo(newAddress);
    assertThat(events(oldEmail, "email_changed")).isZero();

    assertThat(confirm(token, current)).isEqualTo(204);

    // Après confirmation, seul le nouvel e-mail se connecte.
    assertThat(currentEmail(id)).isEqualTo(newAddress);
    assertThat(loginStatus(oldEmail)).isEqualTo(401);
    assertThat(loginStatus(newAddress)).isEqualTo(200);
    // Journal : email_changed (avec les deux adresses) et révocation des autres sessions.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_event where practitioner_id = ? and kind ="
                    + " 'email_changed' and details ->> 'previousEmail' = ? and details ->>"
                    + " 'newEmail' = ?",
                Integer.class,
                id,
                oldEmail,
                newAddress))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_event where practitioner_id = ? and kind ="
                    + " 'session_revoked' and details ->> 'scope' = 'others'",
                Integer.class,
                id))
        .isEqualTo(1);
    assertThat(sessionStatus(other)).isEqualTo(401);
    // La session qui a porté la confirmation survit, avec l'adresse à jour.
    MvcResult kept = mvc.perform(get("/api/auth/session").cookie(current)).andReturn();
    assertThat(kept.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(kept).path("email").asString()).isEqualTo(newAddress);
    // Deux e-mails : lien à la nouvelle adresse, information à l'ancienne.
    String info = mails.lastText(oldEmail, "a changé");
    assertThat(info).isNotNull().contains(newAddress);
    assertThat(mails.lastText(newAddress, "a changé", Duration.ofSeconds(1))).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"docs_pending", "under_review", "needs_more", "approved"})
  void everyConfirmedStatusCanRequestAChange(String status) throws Exception {
    String oldEmail = account(status);
    Cookie cookie = session(oldEmail);
    String newAddress = newEmail();

    assertThat(change(cookie, newAddress).getResponse().getStatus()).isEqualTo(202);

    assertThat(tokens(oldEmail, "email_change")).isEqualTo(1);
    assertThat(linkToken(newAddress)).isNotBlank();
  }

  @Test
  void anAccountRefusedAfterLoginChangesNothing() throws Exception {
    String oldEmail = account("approved");
    Cookie cookie = session(oldEmail);
    jdbc.update(
        "update practitioner set verification_status = 'rejected' where email_normalized = ?",
        oldEmail);

    assertThat(change(cookie, newEmail()).getResponse().getStatus()).isEqualTo(202);

    assertThat(tokens(oldEmail, "email_change")).isZero();
  }

  // ---- jeton : expiration, usage unique, remplacement

  @Test
  void anExpiredTokenGives410_andLeavesTheEmailUntouched() throws Exception {
    String oldEmail = account();
    UUID id = idOf(oldEmail);
    String newAddress = newEmail();
    change(session(oldEmail), newAddress);
    String token = linkToken(newAddress);

    clock.advance(Duration.ofHours(24).plusMinutes(1));

    MvcResult expired = post(CONFIRM, Map.of("token", token), null, IP);
    assertThat(expired.getResponse().getStatus()).isEqualTo(410);
    assertThat(body(expired).path("code").asString()).isEqualTo("TOKEN_INVALID_OR_EXPIRED");
    assertThat(currentEmail(id)).isEqualTo(oldEmail);
    assertThat(events(oldEmail, "email_changed")).isZero();
  }

  @Test
  void aTokenStillValidJustBeforeItsExpiryChangesTheEmail() throws Exception {
    String oldEmail = account();
    UUID id = idOf(oldEmail);
    String newAddress = newEmail();
    change(session(oldEmail), newAddress);
    String token = linkToken(newAddress);

    clock.advance(Duration.ofHours(23).plusMinutes(59));

    assertThat(confirm(token, null)).isEqualTo(204);
    assertThat(currentEmail(id)).isEqualTo(newAddress);
  }

  @Test
  void aReusedTokenGives410() throws Exception {
    String oldEmail = account();
    UUID id = idOf(oldEmail);
    String newAddress = newEmail();
    change(session(oldEmail), newAddress);
    String token = linkToken(newAddress);

    assertThat(confirm(token, null)).isEqualTo(204);
    assertThat(confirm(token, null)).isEqualTo(410);

    assertThat(currentEmail(id)).isEqualTo(newAddress);
    assertThat(events(newAddress, "email_changed")).isEqualTo(1);
  }

  @Test
  void aNewRequestInvalidatesThePreviousLink() throws Exception {
    String oldEmail = account();
    UUID id = idOf(oldEmail);
    Cookie cookie = session(oldEmail);
    String first = newEmail();
    String second = newEmail();
    change(cookie, first);
    String firstToken = linkToken(first);
    clock.advance(Duration.ofMinutes(2));
    change(cookie, second);
    String secondToken = linkToken(second);

    assertThat(confirm(firstToken, null)).isEqualTo(410);
    assertThat(confirm(secondToken, null)).isEqualTo(204);

    assertThat(currentEmail(id)).isEqualTo(second);
  }

  @Test
  void requestsAreLimitedToOnePerMinuteWithoutSayingSo() throws Exception {
    String oldEmail = account();
    Cookie cookie = session(oldEmail);
    String first = newEmail();
    String second = newEmail();

    assertThat(change(cookie, first).getResponse().getStatus()).isEqualTo(202);
    assertThat(change(cookie, second).getResponse().getStatus()).isEqualTo(202);

    assertThat(tokens(oldEmail, "email_change")).isEqualTo(1);
    assertThat(mails.count(second, LINK_SUBJECT)).isZero();
  }

  @Test
  void aTokenOfAnotherKindCannotChangeTheEmail_andViceVersa() throws Exception {
    String oldEmail = account();
    UUID id = idOf(oldEmail);
    String newAddress = newEmail();
    change(session(oldEmail), newAddress);
    String token = linkToken(newAddress);

    // Un jeton de changement d'e-mail ne réinitialise pas un mot de passe.
    MvcResult reset =
        post(
            "/api/auth/password-reset/confirm",
            Map.of("token", token, "password", "Autre-Mot-De-Passe-77"),
            null,
            IP);
    assertThat(reset.getResponse().getStatus()).isEqualTo(410);
    // Il reste utilisable pour son usage.
    assertThat(confirm(token, null)).isEqualTo(204);
    assertThat(currentEmail(id)).isEqualTo(newAddress);
  }

  // ---- sessions

  @Test
  void confirmingWithoutASessionRevokesEverySessionOfTheAccount() throws Exception {
    String oldEmail = account();
    String newAddress = newEmail();
    Cookie first = session(oldEmail);
    Cookie second = session(oldEmail);
    change(first, newAddress);
    String token = linkToken(newAddress);

    assertThat(confirm(token, null)).isEqualTo(204);

    assertThat(sessionStatus(first)).isEqualTo(401);
    assertThat(sessionStatus(second)).isEqualTo(401);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_event e join practitioner p on p.id ="
                    + " e.practitioner_id where p.email_normalized = ? and e.kind ="
                    + " 'session_revoked' and e.details ->> 'scope' = 'all'",
                Integer.class,
                newAddress))
        .isEqualTo(1);
  }

  @Test
  void confirmingWithTheSessionOfAnotherAccountKeepsOnlyThatOtherAccountLoggedIn()
      throws Exception {
    String oldEmail = account();
    String newAddress = newEmail();
    Cookie mine = session(oldEmail);
    String stranger = account();
    Cookie strangerSession = session(stranger);
    change(mine, newAddress);
    String token = linkToken(newAddress);

    assertThat(confirm(token, strangerSession)).isEqualTo(204);

    assertThat(sessionStatus(mine)).isEqualTo(401);
    assertThat(sessionStatus(strangerSession)).isEqualTo(200);
  }

  // ---- mot de passe

  @Test
  void wrongPasswordsCountPerAccountWhateverTheIp_andDoNotLockTheLogin() throws Exception {
    String oldEmail = account();
    Cookie cookie = session(oldEmail);
    String newAddress = newEmail();

    // Quatre essais faux depuis deux IP différentes : toujours 422 INVALID_PASSWORD.
    for (int i = 0; i < 4; i++) {
      String ip = i % 2 == 0 ? "198.51.100.11" : "198.51.100.22";
      MvcResult wrong = change(cookie, newAddress, "Mauvais-Mot-De-Passe-1", ip);
      assertThat(wrong.getResponse().getStatus()).isEqualTo(422);
      JsonNode error = body(wrong).path("errors").get(0);
      assertThat(error.path("field").asString()).isEqualTo("password");
      assertThat(error.path("code").asString()).isEqualTo("INVALID_PASSWORD");
    }
    // Le cinquième, depuis une troisième IP, verrouille le compte (clé par compte, G Q49).
    MvcResult locked = change(cookie, newAddress, "Mauvais-Mot-De-Passe-1", "198.51.100.33");
    assertThat(locked.getResponse().getStatus()).isEqualTo(423);
    assertThat(locked.getResponse().getHeader("Retry-After")).isNotNull();

    // Même le bon mot de passe est refusé, depuis n'importe quelle IP.
    assertThat(change(cookie, newAddress, PASSWORD, "198.51.100.44").getResponse().getStatus())
        .isEqualTo(423);
    assertThat(tokens(oldEmail, "email_change")).isZero();
    assertThat(mails.count(newAddress, LINK_SUBJECT)).isZero();
    // Le verrou du login est distinct : la connexion reste possible.
    assertThat(login(oldEmail, PASSWORD, null, "198.51.100.55").getResponse().getStatus())
        .isEqualTo(200);
    // Le verrou expire après 15 minutes.
    clock.advance(Duration.ofMinutes(16));
    assertThat(change(cookie, newAddress, PASSWORD, "198.51.100.44").getResponse().getStatus())
        .isEqualTo(202);
    assertThat(tokens(oldEmail, "email_change")).isEqualTo(1);
  }

  // ---- bascule : réinitialisation, statut

  @Test
  void aPasswordResetLinkSentToTheOldAddressIsDeadOnceTheEmailChanged() throws Exception {
    String oldEmail = account();
    UUID id = idOf(oldEmail);
    String newAddress = newEmail();
    post("/api/auth/password-reset/request", Map.of("email", oldEmail), null, IP);
    String resetText = mails.lastText(oldEmail, "réinitialisation");
    assertThat(resetText).isNotNull();
    Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(resetText);
    assertThat(m.find()).isTrue();
    String resetToken = m.group(1);
    change(session(oldEmail), newAddress);
    String token = linkToken(newAddress);

    assertThat(confirm(token, null)).isEqualTo(204);

    MvcResult reset =
        post(
            "/api/auth/password-reset/confirm",
            Map.of("token", resetToken, "password", "Autre-Mot-De-Passe-77"),
            null,
            IP);
    assertThat(reset.getResponse().getStatus()).isEqualTo(410);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from auth_token where practitioner_id = ? and kind ="
                    + " 'password_reset' and consumed_at is null",
                Integer.class,
                id))
        .isZero();
    // Le mot de passe n'a pas changé.
    assertThat(login(newAddress, PASSWORD, null, IP).getResponse().getStatus()).isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"suspended", "rejected", "closed"})
  void anAccountTurnedSuspendedRefusedOrClosedSinceTheRequestKeepsItsEmail(String status)
      throws Exception {
    String oldEmail = account("approved");
    UUID id = idOf(oldEmail);
    String newAddress = newEmail();
    change(session(oldEmail), newAddress);
    String token = linkToken(newAddress);
    jdbc.update("update practitioner set verification_status = ? where id = ?", status, id);

    MvcResult result = post(CONFIRM, Map.of("token", token), null, IP);

    assertThat(result.getResponse().getStatus()).isEqualTo(410);
    assertThat(currentEmail(id)).isEqualTo(oldEmail);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_event where practitioner_id = ? and kind ="
                    + " 'email_changed'",
                Integer.class,
                id))
        .isZero();
  }

  // ---- anti-énumération

  @Test
  void anAddressAlreadyUsedByAnotherAccountIsIndistinguishableFromAFreeOne() throws Exception {
    String oldEmail = account();
    Cookie cookie = session(oldEmail);
    String taken = account("approved");
    String free = newEmail();

    MvcResult onTaken = change(cookie, taken);
    // Hors limite d'une par minute : la demande suivante est comparée à la première.
    clock.advance(Duration.ofMinutes(2));
    MvcResult onFree = change(cookie, free);

    assertThat(onTaken.getResponse().getStatus()).isEqualTo(202);
    assertThat(onFree.getResponse().getStatus()).isEqualTo(202);
    assertThat(onTaken.getResponse().getContentAsString())
        .isEqualTo(onFree.getResponse().getContentAsString());
    assertThat(onTaken.getResponse().getHeaderNames())
        .containsExactlyInAnyOrderElementsOf(onFree.getResponse().getHeaderNames());
    // Le travail en base est le même : un jeton par demande (le premier est remplacé).
    assertThat(tokens(oldEmail, "email_change")).isEqualTo(2);

    // L'adresse prise reçoit « vous avez déjà un compte », jamais de lien de confirmation.
    assertThat(mails.lastText(taken, "déjà un compte"))
        .isNotNull()
        .doesNotContain("/confirmer-email");
    assertThat(mails.count(taken, LINK_SUBJECT)).isZero();
    // L'adresse libre reçoit le lien.
    assertThat(linkToken(free)).isNotBlank();
    assertThat(mails.count(free, "déjà un compte")).isZero();
  }

  @Test
  void anAddressTakenBetweenRequestAndConfirmationGives410() throws Exception {
    String oldEmail = account();
    UUID id = idOf(oldEmail);
    String newAddress = newEmail();
    change(session(oldEmail), newAddress);
    String token = linkToken(newAddress);
    // Un autre compte prend l'adresse avant la confirmation.
    registerOn(newAddress);

    MvcResult result = post(CONFIRM, Map.of("token", token), null, IP);

    assertThat(result.getResponse().getStatus()).isEqualTo(410);
    assertThat(currentEmail(id)).isEqualTo(oldEmail);
    assertThat(events(oldEmail, "email_changed")).isZero();
  }

  // ---- validation et variante publique

  @Test
  void withoutASessionTheEmailFieldIsRequired() throws Exception {
    MvcResult r = post(CHANGE, Map.of("password", PASSWORD, "newEmail", newEmail()), null, IP);

    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    JsonNode error = body(r).path("errors").get(0);
    assertThat(error.path("field").asString()).isEqualTo("email");
    assertThat(error.path("code").asString()).isEqualTo("INVALID_FIELD");
  }

  @Test
  void withASessionTheNewEmailAndPasswordAreValidated() throws Exception {
    Cookie cookie = session(account());

    MvcResult malformed = change(cookie, "pas-une-adresse");
    MvcResult missing = post(CHANGE, Map.of("password", PASSWORD), cookie, IP);

    assertThat(malformed.getResponse().getStatus()).isEqualTo(422);
    assertThat(body(malformed).path("errors").get(0).path("field").asString())
        .isEqualTo("newEmail");
    assertThat(missing.getResponse().getStatus()).isEqualTo(422);
  }

  @Test
  void thePublicVariantStillIgnoresAConfirmedAccount() throws Exception {
    String oldEmail = account();
    UUID id = idOf(oldEmail);
    String newAddress = newEmail();

    MvcResult r =
        post(
            CHANGE,
            Map.of("email", oldEmail, "password", PASSWORD, "newEmail", newAddress),
            null,
            IP);

    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    assertThat(currentEmail(id)).isEqualTo(oldEmail);
    assertThat(tokens(oldEmail, "email_change")).isZero();
    assertThat(mails.count(newAddress, LINK_SUBJECT)).isZero();
  }

  /** Inscription d'un autre compte sur une adresse donnée, pour simuler la prise de l'adresse. */
  private void registerOn(String email) throws Exception {
    MvcResult r =
        post(
            "/api/auth/register",
            RegistrationFixtures.payload(email, PASSWORD, "psychologist"),
            null,
            IP);
    assertThat(r.getResponse().getStatus()).isEqualTo(201);
  }
}
