package com.psymate.identity.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.psymate.identity.auth.AbstractLoginIT;
import com.psymate.testsupport.TotpGenerator;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M7-08 : PASSWORD_CHANGE_REQUIRED au login et POST /api/auth/password/change (public, car le 403
 * ne laisse aucune session).
 */
class PasswordChangeIT extends AbstractLoginIT {

  private static final String NEW_PASSWORD = "Nouveau-Tournesol-77";
  private static final String CHANGE_URL = "/api/auth/password/change";

  private MvcResult change(String email, String current, String next, String otp) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", email);
    body.put("currentPassword", current);
    body.put("newPassword", next);
    if (otp != null) {
      body.put("otp", otp);
    }
    return post(CHANGE_URL, body, null, IP);
  }

  private void requirePasswordChange(String email) {
    jdbc.update(
        "update practitioner set preferences = preferences ||"
            + " '{\"security.mustChangePassword\": true}'::jsonb where email_normalized = ?",
        email);
  }

  @Test
  void loginIsRefusedWith403PasswordChangeRequiredAndOpensNoSession() throws Exception {
    String email = account();
    requirePasswordChange(email);

    MvcResult r = login(email, PASSWORD, null, IP);

    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(body(r).path("code").asString()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
    assertThat(body(r).path("title").asString()).isNotBlank();
    assertThat(body(r).path("detail").asString()).isNotBlank();
    assertThat(r.getResponse().getCookie("SESSION")).isNull();
    assertThat(events(email, "login_ok")).isZero();
    // Un mauvais mot de passe reste un 401 uniforme : le drapeau n'est révélé qu'après le bon.
    assertThat(login(email, "Mauvais-Mot-De-Passe-1", null, IP).getResponse().getStatus())
        .isEqualTo(401);
  }

  @Test
  void weakOrUnchangedNewPasswordIs422AndNothingChanges() throws Exception {
    String email = account();
    requirePasswordChange(email);

    MvcResult weak = change(email, PASSWORD, "trop-court", null);
    assertThat(weak.getResponse().getStatus()).isEqualTo(422);
    assertThat(body(weak).path("code").asString()).isEqualTo("PASSWORD_POLICY");

    MvcResult same = change(email, PASSWORD, PASSWORD, null);
    assertThat(same.getResponse().getStatus()).isEqualTo(422);
    assertThat(body(same).path("errors").get(0).path("field").asString()).isEqualTo("newPassword");
    assertThat(body(same).path("errors").get(0).path("code").asString())
        .isEqualTo("PASSWORD_UNCHANGED");

    assertThat(events(email, "password_changed")).isZero();
    assertThat(login(email, PASSWORD, null, IP).getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  void wrongCurrentPasswordIs401AndUnknownEmailLooksTheSame() throws Exception {
    String email = account();

    MvcResult wrong = change(email, "Mauvais-Mot-De-Passe-1", NEW_PASSWORD, null);
    MvcResult unknown = change(newEmail(), PASSWORD, NEW_PASSWORD, null);

    assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
    assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
    assertThat(body(wrong).path("code").asString()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(body(unknown).path("code").asString()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(login(email, PASSWORD, null, IP).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void fiveWrongCurrentPasswordsLockLikeTheLogin() throws Exception {
    String email = account();
    for (int i = 1; i <= 4; i++) {
      assertThat(change(email, "Mauvais-Mot-De-Passe-" + i, NEW_PASSWORD, null).getResponse())
          .extracting(r -> r.getStatus())
          .isEqualTo(401);
    }
    MvcResult fifth = change(email, "Mauvais-Mot-De-Passe-5", NEW_PASSWORD, null);
    assertThat(fifth.getResponse().getStatus()).isEqualTo(423);
    assertThat(fifth.getResponse().getHeader("Retry-After")).isNotNull();
  }

  @Test
  void successReturns204_revokesEverySessionAndRecordsTheEvent() throws Exception {
    String email = account();
    Cookie before = session(email);
    assertThat(
            mvc.perform(get("/api/auth/me").cookie(before)).andReturn().getResponse().getStatus())
        .isEqualTo(200);
    requirePasswordChange(email);

    MvcResult r = change(email, PASSWORD, NEW_PASSWORD, null);

    assertThat(r.getResponse().getStatus()).isEqualTo(204);
    assertThat(r.getResponse().getContentAsString()).isEmpty();
    assertThat(
            mvc.perform(get("/api/auth/me").cookie(before)).andReturn().getResponse().getStatus())
        .isEqualTo(401);
    assertThat(events(email, "password_changed")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select preferences::text like '%mustChangePassword%' from practitioner where"
                    + " email_normalized = ?",
                Boolean.class, email))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "select password_hash from practitioner where email_normalized = ?",
                String.class,
                email))
        .startsWith("$argon2");
    assertThat(login(email, PASSWORD, null, IP).getResponse().getStatus()).isEqualTo(401);
    assertThat(login(email, NEW_PASSWORD, null, IP).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void withTwoFactorActiveTheChangeNeedsTheCode() throws Exception {
    String email = account();
    Cookie session = session(email);
    String secret = body(post("/api/me/2fa/enable", null, session, IP)).path("secret").asString();
    post(
        "/api/me/2fa/verify",
        Map.of("code", TotpGenerator.code(secret, clock.instant())),
        session,
        IP);
    clock.advance(Duration.ofSeconds(30));

    MvcResult noCode = change(email, PASSWORD, NEW_PASSWORD, null);
    assertThat(noCode.getResponse().getStatus()).isEqualTo(202);
    assertThat(body(noCode).path("otpRequired").asBoolean()).isTrue();

    MvcResult badCode = change(email, PASSWORD, NEW_PASSWORD, "000000");
    assertThat(badCode.getResponse().getStatus()).isEqualTo(401);

    // Un 422 sur le nouveau mot de passe ne consomme pas le code : le même pas TOTP sert ensuite.
    String code = TotpGenerator.code(secret, clock.instant());
    MvcResult weak = change(email, PASSWORD, "trop-court", code);
    assertThat(weak.getResponse().getStatus()).isEqualTo(422);
    assertThat(body(weak).path("code").asString()).isEqualTo("PASSWORD_POLICY");

    MvcResult ok = change(email, PASSWORD, NEW_PASSWORD, code);
    assertThat(ok.getResponse().getStatus()).isEqualTo(204);
    assertThat(events(email, "password_changed")).isEqualTo(1);
  }
}
