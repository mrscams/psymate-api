package com.psymate.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Fiche 11 / UC-19 : activation, connexion avec code, codes de secours, désactivation. */
class TotpIT extends AbstractLoginIT {

  /** Compte connecté dont la 2FA est active ; secret en Base32 et codes de secours. */
  private record Enrolled(String email, Cookie session, String secret, List<String> recovery) {}

  private String codeNow(String secret) {
    return TotpGenerator.code(secret, clock.instant());
  }

  private Enrolled enrolled() throws Exception {
    String email = account();
    Cookie session = session(email);
    MvcResult enable = post("/api/me/2fa/enable", null, session, IP);
    assertThat(enable.getResponse().getStatus()).isEqualTo(200);
    String secret = body(enable).path("secret").asString();
    MvcResult verify = post("/api/me/2fa/verify", Map.of("code", codeNow(secret)), session, IP);
    assertThat(verify.getResponse().getStatus()).isEqualTo(200);
    List<String> codes = new ArrayList<>();
    for (JsonNode n : body(verify).path("recoveryCodes")) {
      codes.add(n.asString());
    }
    return new Enrolled(email, session, secret, codes);
  }

  @Test
  void enableReturnsSecretAndOtpauthUri_secretIsStoredEncrypted() throws Exception {
    String email = account();
    Cookie session = session(email);
    MvcResult r = post("/api/me/2fa/enable", null, session, IP);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    String secret = body(r).path("secret").asString();
    String uri = body(r).path("otpauthUri").asString();
    assertThat(secret).matches("[A-Z2-7]{32}");
    assertThat(uri)
        .startsWith("otpauth://totp/PsyMate:")
        .contains("secret=" + secret)
        .contains("issuer=PsyMate");

    byte[] stored =
        jdbc.queryForObject(
            "select totp_secret_enc from practitioner where email_normalized = ?",
            byte[].class,
            email);
    assertThat(stored).isNotNull().hasSizeGreaterThan(20);
    byte[] plain = TotpGenerator.base32Decode(secret);
    assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain(new String(plain, java.nio.charset.StandardCharsets.ISO_8859_1));
    // En attente : pas encore active, la connexion ne demande pas de code.
    assertThat(
            jdbc.queryForObject(
                "select totp_enabled_at is null from practitioner where email_normalized = ?",
                Boolean.class,
                email))
        .isTrue();
    assertThat(login(email, PASSWORD, null, IP).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void wrongVerificationCodeDoesNotActivate_rightOneActivatesAndReturnsTenHashedCodes()
      throws Exception {
    String email = account();
    Cookie session = session(email);
    String secret = body(post("/api/me/2fa/enable", null, session, IP)).path("secret").asString();

    String wrong = String.valueOf((Integer.parseInt(codeNow(secret)) + 1) % 1_000_000);
    wrong = "0".repeat(6 - wrong.length()) + wrong;
    MvcResult bad = post("/api/me/2fa/verify", Map.of("code", wrong), session, IP);
    assertThat(bad.getResponse().getStatus()).isEqualTo(422);
    assertThat(body(bad).path("errors").get(0).path("field").asString()).isEqualTo("code");
    assertThat(events(email, "2fa_enabled")).isZero();

    MvcResult ok = post("/api/me/2fa/verify", Map.of("code", codeNow(secret)), session, IP);
    assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    JsonNode codes = body(ok).path("recoveryCodes");
    assertThat(codes).hasSize(10);
    assertThat(events(email, "2fa_enabled")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select totp_enabled_at is not null from practitioner where email_normalized = ?",
                Boolean.class,
                email))
        .isTrue();
    List<String> hashes =
        jdbc.queryForList(
            "select code_hash from totp_recovery_code where practitioner_id = ? and used_at is"
                + " null",
            String.class,
            idOf(email));
    assertThat(hashes).hasSize(10).doesNotContainAnyElementsOf(textsOf(codes));
    assertThat(hashes).allMatch(h -> h.matches("[0-9a-f]{64}"));
    // Deuxième activation refusée.
    assertThat(post("/api/me/2fa/enable", null, session, IP).getResponse().getStatus())
        .isEqualTo(409);
  }

  private static List<String> textsOf(JsonNode array) {
    List<String> out = new ArrayList<>();
    for (JsonNode n : array) {
      out.add(n.asString());
    }
    return out;
  }

  @Test
  void loginAsksForOtp_thenAcceptsValidCode_andRefusesReuse() throws Exception {
    Enrolled e = enrolled();

    MvcResult noOtp = login(e.email(), PASSWORD, null, IP);
    assertThat(noOtp.getResponse().getStatus()).isEqualTo(202);
    assertThat(body(noOtp).path("otpRequired").asBoolean()).isTrue();
    assertThat(noOtp.getResponse().getCookie("SESSION")).isNull();

    MvcResult wrongOtp = login(e.email(), PASSWORD, "000000", IP);
    assertThat(wrongOtp.getResponse().getStatus()).isEqualTo(401);
    MvcResult wrongPassword = login(e.email(), "Mauvais-Mot-De-Passe-1", "000000", IP);
    assertThat(wrongOtp.getResponse().getContentAsString())
        .isEqualTo(wrongPassword.getResponse().getContentAsString());

    String code = codeNow(e.secret());
    MvcResult ok = login(e.email(), PASSWORD, code, IP);
    assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    assertThat(ok.getResponse().getCookie("SESSION")).isNotNull();

    // Le même code, dans le même pas de temps, ne sert qu'une fois.
    assertThat(login(e.email(), PASSWORD, code, IP).getResponse().getStatus()).isEqualTo(401);

    // Le pas suivant donne un nouveau code valide.
    clock.advance(Duration.ofSeconds(30));
    assertThat(login(e.email(), PASSWORD, codeNow(e.secret()), IP).getResponse().getStatus())
        .isEqualTo(200);
    assertThat(events(e.email(), "login_ok")).isEqualTo(2 + 1); // 1 session de préparation
    assertThat(events(e.email(), "login_failed")).isEqualTo(3);
  }

  @Test
  void recoveryCodeWorksOnceAndIsMarkedUsed() throws Exception {
    Enrolled e = enrolled();
    String recovery = e.recovery().get(0);

    MvcResult ok = login(e.email(), PASSWORD, recovery.toLowerCase(), IP);
    assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    assertThat(events(e.email(), "recovery_code_used")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from totp_recovery_code where practitioner_id = ? and used_at is"
                    + " not null",
                Integer.class,
                idOf(e.email())))
        .isEqualTo(1);

    assertThat(login(e.email(), PASSWORD, recovery, IP).getResponse().getStatus()).isEqualTo(401);
    // Un autre code de secours reste utilisable.
    assertThat(login(e.email(), PASSWORD, e.recovery().get(1), IP).getResponse().getStatus())
        .isEqualTo(200);
  }

  @Test
  void regenerateReplacesRecoveryCodes() throws Exception {
    Enrolled e = enrolled();
    MvcResult r = post("/api/me/2fa/recovery-codes/regenerate", null, e.session(), IP);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    List<String> fresh = textsOf(body(r).path("recoveryCodes"));
    assertThat(fresh).hasSize(10).doesNotContainAnyElementsOf(e.recovery());

    assertThat(login(e.email(), PASSWORD, e.recovery().get(0), IP).getResponse().getStatus())
        .isEqualTo(401);
    assertThat(login(e.email(), PASSWORD, fresh.get(0), IP).getResponse().getStatus())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from totp_recovery_code where practitioner_id = ? and used_at is"
                    + " null",
                Integer.class,
                idOf(e.email())))
        .isEqualTo(9);
  }

  @Test
  void disableNeedsPasswordAndCode_thenLoginNoLongerAsksForOtp() throws Exception {
    Enrolled e = enrolled();
    MvcResult badPassword =
        post(
            "/api/me/2fa/disable",
            Map.of("password", "Mauvais-Mot-De-Passe-1", "code", codeNow(e.secret())),
            e.session(),
            IP);
    assertThat(badPassword.getResponse().getStatus()).isEqualTo(422);
    assertThat(body(badPassword).path("errors").get(0).path("field").asString())
        .isEqualTo("password");
    MvcResult badCode =
        post(
            "/api/me/2fa/disable", Map.of("password", PASSWORD, "code", "000000"), e.session(), IP);
    assertThat(badCode.getResponse().getStatus()).isEqualTo(422);
    assertThat(events(e.email(), "2fa_disabled")).isZero();

    MvcResult ok =
        post(
            "/api/me/2fa/disable",
            Map.of("password", PASSWORD, "code", codeNow(e.secret())),
            e.session(),
            IP);
    assertThat(ok.getResponse().getStatus()).isEqualTo(204);
    assertThat(events(e.email(), "2fa_disabled")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select totp_secret_enc is null and totp_enabled_at is null from practitioner"
                    + " where email_normalized = ?",
                Boolean.class,
                e.email()))
        .isTrue();
    assertThat(login(e.email(), PASSWORD, null, IP).getResponse().getStatus()).isEqualTo(200);
    // Désactivée : ni disable ni regenerate.
    assertThat(
            post("/api/me/2fa/recovery-codes/regenerate", null, e.session(), IP)
                .getResponse()
                .getStatus())
        .isEqualTo(409);
  }

  @Test
  void disableAcceptsARecoveryCode() throws Exception {
    Enrolled e = enrolled();
    MvcResult ok =
        post(
            "/api/me/2fa/disable",
            Map.of("password", PASSWORD, "code", e.recovery().get(0)),
            e.session(),
            IP);
    assertThat(ok.getResponse().getStatus()).isEqualTo(204);
  }

  @Test
  void fiveWrongVerificationCodesLockTheEndpoint() throws Exception {
    String email = account();
    Cookie session = session(email);
    String secret = body(post("/api/me/2fa/enable", null, session, IP)).path("secret").asString();
    for (int i = 0; i < 4; i++) {
      assertThat(
              post("/api/me/2fa/verify", Map.of("code", "000000"), session, IP)
                  .getResponse()
                  .getStatus())
          .isEqualTo(422);
    }
    MvcResult locked = post("/api/me/2fa/verify", Map.of("code", "000000"), session, IP);
    assertThat(locked.getResponse().getStatus()).isEqualTo(423);
    assertThat(locked.getResponse().getHeader("Retry-After")).isNotNull();
    assertThat(
            post("/api/me/2fa/verify", Map.of("code", codeNow(secret)), session, IP)
                .getResponse()
                .getStatus())
        .isEqualTo(423);
  }

  @Test
  void twoFactorEndpointsRequireASession() throws Exception {
    assertThat(post("/api/me/2fa/enable", null, null, IP).getResponse().getStatus()).isEqualTo(401);
    assertThat(
            post("/api/me/2fa/verify", Map.of("code", "123456"), null, IP)
                .getResponse()
                .getStatus())
        .isEqualTo(401);
    assertThat(
            post("/api/me/2fa/recovery-codes/regenerate", null, null, IP).getResponse().getStatus())
        .isEqualTo(401);
  }
}
