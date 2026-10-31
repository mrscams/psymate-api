package com.psymate.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Fiche 11 : verrouillage, uniformité des erreurs, statuts de compte, sessions (M1-11). */
class LoginHardeningIT extends AbstractLoginIT {

  @Test
  void fiveFailuresLockWith423AndRetryAfter_andSixthCorrectAttemptFailsToo() throws Exception {
    String email = account();
    for (int i = 1; i <= 4; i++) {
      assertThat(login(email, "Mauvais-Mot-De-Passe-" + i, null, IP).getResponse().getStatus())
          .isEqualTo(401);
    }
    MvcResult fifth = login(email, "Mauvais-Mot-De-Passe-5", null, IP);
    assertThat(fifth.getResponse().getStatus()).isEqualTo(423);
    long retry = Long.parseLong(fifth.getResponse().getHeader("Retry-After"));
    assertThat(retry).isBetween(890L, 900L);
    assertThat(body(fifth).path("code").asString()).isEqualTo("ACCOUNT_LOCKED");

    MvcResult sixth = login(email, PASSWORD, null, IP);
    assertThat(sixth.getResponse().getStatus()).isEqualTo(423);
    assertThat(sixth.getResponse().getHeader("Retry-After")).isNotNull();
    assertThat(sixth.getResponse().getCookie("SESSION")).isNull();

    assertThat(events(email, "login_failed")).isEqualTo(5);
    assertThat(events(email, "locked")).isEqualTo(1);
    assertThat(events(email, "login_ok")).isZero();
    assertThat(
            jdbc.queryForObject(
                "select failed_logins from practitioner where email_normalized = ?",
                Integer.class,
                email))
        .isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "select locked_until is not null from practitioner where email_normalized = ?",
                Boolean.class,
                email))
        .isTrue();

    // Le verrou porte sur la clé e-mail + IP : une autre adresse IP n'est pas concernée.
    assertThat(login(email, PASSWORD, null, "198.51.100.7").getResponse().getStatus())
        .isEqualTo(200);

    // Après 15 minutes le verrou tombe.
    clock.advance(Duration.ofMinutes(15).plusSeconds(1));
    assertThat(login(email, PASSWORD, null, IP).getResponse().getStatus()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select failed_logins from practitioner where email_normalized = ?",
                Integer.class,
                email))
        .isZero();
    assertThat(events(email, "login_ok")).isEqualTo(2);
  }

  @Test
  void unknownEmailIsLockedLikeAnExistingOne() throws Exception {
    String email = newEmail();
    for (int i = 1; i <= 4; i++) {
      assertThat(login(email, PASSWORD, null, IP).getResponse().getStatus()).isEqualTo(401);
    }
    assertThat(login(email, PASSWORD, null, IP).getResponse().getStatus()).isEqualTo(423);
  }

  @Test
  void unknownEmailAndWrongPasswordGiveSameResponse() throws Exception {
    String email = account();
    MvcResult unknown = login(newEmail(), PASSWORD, null, "198.51.100.1");
    MvcResult wrong = login(email, "Mauvais-Mot-De-Passe-1", null, "198.51.100.2");
    assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
    assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
    assertThat(unknown.getResponse().getContentAsString())
        .isEqualTo(wrong.getResponse().getContentAsString());
    assertThat(body(wrong).path("code").asString()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(events(email, "login_failed")).isEqualTo(1);
  }

  @Test
  void unknownEmailAndWrongPasswordTakeComparableTime() throws Exception {
    String email = account();
    int n = 0;
    for (int i = 0; i < 5; i++) {
      login(newEmail(), PASSWORD, null, "10.1.0." + (++n));
      login(email, "Mauvais-Mot-De-Passe-1", null, "10.1.0." + (++n));
    }
    List<Long> unknown = new ArrayList<>();
    List<Long> wrong = new ArrayList<>();
    for (int i = 0; i < 15; i++) {
      String ip1 = "10.2.0." + (i + 1);
      long t0 = System.nanoTime();
      login(newEmail(), PASSWORD, null, ip1);
      unknown.add(System.nanoTime() - t0);
      String ip2 = "10.3.0." + (i + 1);
      long t1 = System.nanoTime();
      login(email, "Mauvais-Mot-De-Passe-1", null, ip2);
      wrong.add(System.nanoTime() - t1);
    }
    long a = median(unknown);
    long b = median(wrong);
    long diffMs = Math.abs(a - b) / 1_000_000;
    double ratio = (double) Math.max(a, b) / Math.min(a, b);
    assertThat(diffMs <= 150 || ratio <= 1.5)
        .as("médianes : inconnu=%d ms, mauvais mot de passe=%d ms", a / 1_000_000, b / 1_000_000)
        .isTrue();
  }

  private static long median(List<Long> values) {
    List<Long> sorted = new ArrayList<>(values);
    sorted.sort(Long::compare);
    return sorted.get(sorted.size() / 2);
  }

  @Test
  void emailPendingGives403EmailNotConfirmedOnlyWithCorrectPassword() throws Exception {
    String email = account("email_pending");
    MvcResult r = login(email, PASSWORD, null, IP);
    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(body(r).path("code").asString()).isEqualTo("EMAIL_NOT_CONFIRMED");
    assertThat(r.getResponse().getCookie("SESSION")).isNull();
    // Mauvais mot de passe : rien n'est révélé sur l'état du compte.
    assertThat(login(email, "Mauvais-Mot-De-Passe-1", null, IP).getResponse().getStatus())
        .isEqualTo(401);
  }

  @Test
  void suspendedRejectedAndClosedGive403AccountSuspendedAndRevokeSessions() throws Exception {
    for (String status : List.of("suspended", "rejected", "closed")) {
      String email = account();
      Cookie existing = session(email);
      assertThat(
              mvc.perform(get("/api/auth/me").cookie(existing))
                  .andReturn()
                  .getResponse()
                  .getStatus())
          .isEqualTo(200);

      jdbc.update(
          "update practitioner set verification_status = ? where email_normalized = ?",
          status,
          email);
      MvcResult r = login(email, PASSWORD, null, IP);
      assertThat(r.getResponse().getStatus()).as(status).isEqualTo(403);
      assertThat(body(r).path("code").asString()).isEqualTo("ACCOUNT_SUSPENDED");
      assertThat(r.getResponse().getCookie("SESSION")).isNull();

      assertThat(
              mvc.perform(get("/api/auth/me").cookie(existing))
                  .andReturn()
                  .getResponse()
                  .getStatus())
          .as("session existante invalidée (%s)", status)
          .isEqualTo(401);
      assertThat(events(email, "session_revoked")).isEqualTo(1);
      assertThat(login(email, "Mauvais-Mot-De-Passe-1", null, IP).getResponse().getStatus())
          .isEqualTo(401);
    }
  }

  @Test
  void successfulLoginWritesLoginOkAndRememberMeIsOptional() throws Exception {
    String email = account();
    MvcResult r = login(email, PASSWORD, null, IP);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(r).path("email").asString()).isEqualTo(email);
    assertThat(events(email, "login_ok")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select ip::text from security_event e join practitioner p on p.id ="
                    + " e.practitioner_id where p.email_normalized = ? and e.kind = 'login_ok'",
                String.class,
                email))
        .startsWith(IP);
    MvcResult remembered =
        post(
            "/api/auth/login",
            Map.of("email", email, "password", PASSWORD, "rememberMe", true),
            null,
            IP);
    assertThat(remembered.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void revokeOthersKeepsTheCurrentSessionOnly() throws Exception {
    String email = account();
    Cookie first = session(email);
    Cookie second = session(email);
    MvcResult r = post("/api/auth/sessions/revoke-others", null, second, IP);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(r).path("revoked").asInt()).isEqualTo(1);
    assertThat(mvc.perform(get("/api/auth/me").cookie(first)).andReturn().getResponse().getStatus())
        .isEqualTo(401);
    assertThat(
            mvc.perform(get("/api/auth/me").cookie(second)).andReturn().getResponse().getStatus())
        .isEqualTo(200);
    assertThat(events(email, "session_revoked")).isEqualTo(1);
    assertThat(post("/api/auth/sessions/revoke-others", null, null, IP).getResponse().getStatus())
        .isEqualTo(401);
  }

  @Test
  void adminWithoutTwoFactorIsRefusedOnAdminRoutes() throws Exception {
    String admin = account();
    jdbc.update("update practitioner set role = 'admin' where email_normalized = ?", admin);
    Cookie adminSession = session(admin);
    MvcResult denied =
        mvc.perform(get("/api/admin/verifications").cookie(adminSession)).andReturn();
    assertThat(denied.getResponse().getStatus()).isEqualTo(403);
    assertThat(body(denied).path("code").asString()).isEqualTo("TWO_FACTOR_REQUIRED");

    // Un praticien ordinaire n'est pas concerné par ce filtre (la route n'existe pas encore).
    Cookie plain = session(account());
    MvcResult other = mvc.perform(get("/api/admin/verifications").cookie(plain)).andReturn();
    assertThat(other.getResponse().getContentAsString()).doesNotContain("TWO_FACTOR_REQUIRED");

    // Admin avec 2FA active : le filtre laisse passer.
    jdbc.update(
        "update practitioner set totp_secret_enc = ?, totp_enabled_at = now() where"
            + " email_normalized = ?",
        new byte[] {1},
        admin);
    MvcResult allowed =
        mvc.perform(get("/api/admin/verifications").cookie(adminSession)).andReturn();
    assertThat(allowed.getResponse().getContentAsString()).doesNotContain("TWO_FACTOR_REQUIRED");
  }
}
