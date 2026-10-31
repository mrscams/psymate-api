package com.psymate.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.psymate.identity.auth.AuthTestConfig.MutableClock;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M1-10 : confirmation d'e-mail (fiche 25). /api/patients n'existe pas encore : la route protégée
 * hors /api/auth utilisée ici est GET /api/vocabulary/emotions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthTestConfig.class)
class EmailConfirmationIT {

  private static final String PASSWORD = RegistrationFixtures.PASSWORD;
  private static final String PROTECTED = "/api/vocabulary/emotions";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;
  @Autowired private GenericContainer<?> mailpit;
  private final JsonMapper json = JsonMapper.builder().build();
  private MailpitClient mails;

  @BeforeEach
  void setUp() {
    clock.reset();
    mails = new MailpitClient(mailpit);
  }

  private static String newEmail() {
    return "c" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
  }

  private MvcResult postJson(String url, Map<String, Object> payload, Cookie session)
      throws Exception {
    var req =
        MockMvcRequestBuilders.post(url)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(payload));
    if (session != null) {
      req.cookie(session);
    }
    return mvc.perform(req).andReturn();
  }

  private String registerAndGetToken(String email) throws Exception {
    MvcResult r =
        postJson(
            "/api/auth/register",
            RegistrationFixtures.payload(email, PASSWORD, "psychologist"),
            null);
    assertThat(r.getResponse().getStatus()).isEqualTo(201);
    String token = mails.confirmationToken(email);
    assertThat(token).isNotNull();
    return token;
  }

  /**
   * Session d'un compte email_pending. Depuis M1-11 la connexion est refusée (403) pour ce statut :
   * on ouvre la session le temps d'un passage en docs_pending, puis on remet l'état et on
   * rafraîchit le principal via /api/auth/session.
   */
  private Cookie login(String email) throws Exception {
    setStatus(email, "docs_pending");
    MvcResult r =
        postJson(
            "/api/auth/login",
            Map.of("email", email, "password", PASSWORD, "rememberMe", false),
            null);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    Cookie cookie = r.getResponse().getCookie("SESSION");
    setStatus(email, "email_pending");
    mvc.perform(get("/api/auth/session").cookie(cookie)).andReturn();
    return cookie;
  }

  private void setStatus(String email, String status) {
    jdbc.update(
        "update practitioner set verification_status = ? where email_normalized = ?",
        status,
        email);
  }

  private int confirm(String token) throws Exception {
    return postJson("/api/auth/email/confirm", Map.of("token", token), null)
        .getResponse()
        .getStatus();
  }

  private String status(String email) {
    return jdbc.queryForObject(
        "select verification_status from practitioner where email_normalized = ?",
        String.class,
        email);
  }

  /** Le filtre travaille sur le chemin décodé : /%61pi/… est bien /api/… (M1-14b). */
  @Test
  void percentEncodedPathCannotBypassTheFilter() throws Exception {
    String email = newEmail();
    registerAndGetToken(email);
    Cookie pending = login(email);

    MvcResult denied =
        mvc.perform(get(java.net.URI.create("/%61pi/vocabulary/emotions")).cookie(pending))
            .andReturn();
    assertThat(denied.getResponse().getStatus()).isEqualTo(403);
    assertThat(json.readTree(denied.getResponse().getContentAsString()).path("code").asString())
        .isEqualTo("EMAIL_NOT_CONFIRMED");
  }

  @Test
  void protectedRouteIs403BeforeConfirmationAnd200After() throws Exception {
    String email = newEmail();
    String token = registerAndGetToken(email);
    Cookie pending = login(email);

    MvcResult denied = mvc.perform(get(PROTECTED).cookie(pending)).andReturn();
    assertThat(denied.getResponse().getStatus()).isEqualTo(403);
    assertThat(denied.getResponse().getContentType()).contains("application/problem+json");
    assertThat(json.readTree(denied.getResponse().getContentAsString()).path("code").asString())
        .isEqualTo("EMAIL_NOT_CONFIRMED");
    // Les routes /api/auth/** restent accessibles.
    assertThat(
            mvc.perform(get("/api/auth/me").cookie(pending)).andReturn().getResponse().getStatus())
        .isEqualTo(200);

    MvcResult confirmed = postJson("/api/auth/email/confirm", Map.of("token", token), null);
    assertThat(confirmed.getResponse().getStatus()).isEqualTo(204);
    Cookie opened = confirmed.getResponse().getCookie("SESSION");
    assertThat(opened).isNotNull();
    assertThat(mvc.perform(get(PROTECTED).cookie(opened)).andReturn().getResponse().getStatus())
        .isEqualTo(200);
  }

  @Test
  void validTokenOpensSessionMovesToDocsPendingAndIsJournalled() throws Exception {
    String email = newEmail();
    String token = registerAndGetToken(email);

    MvcResult r = postJson("/api/auth/email/confirm", Map.of("token", token), null);
    assertThat(r.getResponse().getStatus()).isEqualTo(204);
    String setCookie = r.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie).contains("SESSION=").contains("HttpOnly");

    Map<String, Object> row =
        jdbc.queryForMap(
            "select verification_status, email_confirmed_at from practitioner where"
                + " email_normalized = ?",
            email);
    assertThat(row.get("verification_status")).isEqualTo("docs_pending");
    assertThat(row.get("email_confirmed_at")).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_event s join practitioner p on p.id ="
                    + " s.practitioner_id where p.email_normalized = ? and s.kind ="
                    + " 'email_confirmed'",
                Integer.class,
                email))
        .isEqualTo(1);

    MvcResult session =
        mvc.perform(get("/api/auth/session").cookie(r.getResponse().getCookie("SESSION")))
            .andReturn();
    JsonNode body = json.readTree(session.getResponse().getContentAsString());
    assertThat(body.path("verificationStatus").asString()).isEqualTo("docs_pending");
    assertThat(body.path("emailConfirmed").asBoolean()).isTrue();
    assertThat(body.path("email").asString()).isEqualTo(email);
  }

  @Test
  void sessionEndpointRequiresAuthenticationAndReportsPending() throws Exception {
    assertThat(mvc.perform(get("/api/auth/session")).andReturn().getResponse().getStatus())
        .isEqualTo(401);
    String email = newEmail();
    registerAndGetToken(email);
    MvcResult s = mvc.perform(get("/api/auth/session").cookie(login(email))).andReturn();
    JsonNode body = json.readTree(s.getResponse().getContentAsString());
    assertThat(body.path("verificationStatus").asString()).isEqualTo("email_pending");
    assertThat(body.path("emailConfirmed").asBoolean(true)).isFalse();
  }

  @Test
  void secondUseOfTokenGives410AndNoSession() throws Exception {
    String email = newEmail();
    String token = registerAndGetToken(email);
    assertThat(confirm(token)).isEqualTo(204);
    MvcResult second = postJson("/api/auth/email/confirm", Map.of("token", token), null);
    assertThat(second.getResponse().getStatus()).isEqualTo(410);
    assertThat(second.getResponse().getCookie("SESSION")).isNull();
  }

  @Test
  void tokenOlderThan24HoursGives410AndAccountStaysPending() throws Exception {
    String email = newEmail();
    String token = registerAndGetToken(email);
    clock.advance(Duration.ofHours(24).plusMinutes(1));
    MvcResult r = postJson("/api/auth/email/confirm", Map.of("token", token), null);
    assertThat(r.getResponse().getStatus()).isEqualTo(410);
    assertThat(r.getResponse().getCookie("SESSION")).isNull();
    assertThat(status(email)).isEqualTo("email_pending");
  }

  @Test
  void tokenStillValidJustBefore24Hours() throws Exception {
    String email = newEmail();
    String token = registerAndGetToken(email);
    clock.advance(Duration.ofHours(23).plusMinutes(59));
    assertThat(confirm(token)).isEqualTo(204);
  }

  @Test
  void confirmWithIpv6ZoneAddressStillSucceedsAndStoresNormalizedIp() throws Exception {
    String email = newEmail();
    String token = registerAndGetToken(email);
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/auth/email/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("token", token)))
                    .with(
                        req -> {
                          req.setRemoteAddr("fe80::1%eth0");
                          return req;
                        }))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(204);
    assertThat(
            jdbc.queryForObject(
                "select host(s.ip) from security_event s join practitioner p on p.id ="
                    + " s.practitioner_id where p.email_normalized = ? and s.kind ="
                    + " 'email_confirmed'",
                String.class,
                email))
        .isEqualTo("fe80::1");
  }

  @Test
  void unknownTokenGives410() throws Exception {
    assertThat(confirm("jeton-inconnu")).isEqualTo(410);
  }

  @Test
  void passwordResetTokenCannotConfirmAnEmail() throws Exception {
    String email = newEmail();
    registerAndGetToken(email);
    postJson("/api/auth/password-reset/request", Map.of("email", email), null);
    String resetText = mails.lastText(email, "passe");
    String resetToken = resetText.replaceAll("(?s).*token=([A-Za-z0-9_-]+).*", "$1");
    assertThat(confirm(resetToken)).isEqualTo(410);
    assertThat(status(email)).isEqualTo("email_pending");
  }

  @Test
  void sessionOpenedBeforeConfirmationInAnotherBrowserIsRefreshed() throws Exception {
    String email = newEmail();
    String token = registerAndGetToken(email);
    Cookie pending = login(email);
    assertThat(mvc.perform(get(PROTECTED).cookie(pending)).andReturn().getResponse().getStatus())
        .isEqualTo(403);
    assertThat(confirm(token)).isEqualTo(204);
    assertThat(mvc.perform(get(PROTECTED).cookie(pending)).andReturn().getResponse().getStatus())
        .isEqualTo(200);
  }

  @Test
  void resendForUnknownEmailIs202AndSendsNothing() throws Exception {
    String email = newEmail();
    assertThat(
            postJson("/api/auth/email/resend", Map.of("email", email), null)
                .getResponse()
                .getStatus())
        .isEqualTo(202);
    assertThat(mails.count(email, "confirmez")).isZero();
  }

  @Test
  void resendInvalidatesPreviousTokenAndSendsNewOne() throws Exception {
    String email = newEmail();
    String first = registerAndGetToken(email);
    clock.advance(Duration.ofMinutes(2));
    assertThat(
            postJson("/api/auth/email/resend", Map.of("email", email), null)
                .getResponse()
                .getStatus())
        .isEqualTo(202);
    assertThat(mails.count(email, "confirmez")).isEqualTo(2);
    assertThat(confirm(first)).isEqualTo(410);
    String second =
        jdbc.queryForList(
                "select token_hash from auth_token t join practitioner p on p.id ="
                    + " t.practitioner_id where p.email_normalized = ? and t.consumed_at is null",
                String.class,
                email)
            .getFirst();
    assertThat(second).isNotEqualTo(AuthService.sha256(first));
    // Le nouveau jeton (le plus récent reçu) est accepté.
    String latest = latestToken(email, second);
    assertThat(confirm(latest)).isEqualTo(204);
  }

  /** Parmi les messages reçus, le jeton dont le hash est celui du jeton actif. */
  private String latestToken(String email, String activeHash) throws Exception {
    String token = mails.confirmationToken(email);
    if (AuthService.sha256(token).equals(activeHash)) {
      return token;
    }
    throw new AssertionError("le dernier e-mail ne contient pas le jeton actif");
  }

  @Test
  void resendIsLimitedToOnePerMinute() throws Exception {
    String email = newEmail();
    registerAndGetToken(email);
    // Moins d'une minute après l'inscription : toujours 202, mais rien de nouveau n'est émis.
    assertThat(
            postJson("/api/auth/email/resend", Map.of("email", email), null)
                .getResponse()
                .getStatus())
        .isEqualTo(202);
    assertThat(tokenCount(email)).isEqualTo(1);
    clock.advance(Duration.ofSeconds(61));
    postJson("/api/auth/email/resend", Map.of("email", email), null);
    assertThat(tokenCount(email)).isEqualTo(2);
    postJson("/api/auth/email/resend", Map.of("email", email), null);
    assertThat(tokenCount(email)).isEqualTo(2);
  }

  @Test
  void resendIsLimitedToFivePerDay() throws Exception {
    String email = newEmail();
    registerAndGetToken(email);
    for (int i = 0; i < 8; i++) {
      clock.advance(Duration.ofMinutes(2));
      assertThat(
              postJson("/api/auth/email/resend", Map.of("email", email), null)
                  .getResponse()
                  .getStatus())
          .isEqualTo(202);
    }
    assertThat(tokenCount(email)).isEqualTo(AuthService.RESEND_MAX_PER_DAY);
    clock.advance(Duration.ofHours(25));
    postJson("/api/auth/email/resend", Map.of("email", email), null);
    assertThat(tokenCount(email)).isEqualTo(AuthService.RESEND_MAX_PER_DAY + 1);
  }

  private int tokenCount(String email) {
    return jdbc.queryForObject(
        "select count(*) from auth_token t join practitioner p on p.id = t.practitioner_id where"
            + " p.email_normalized = ? and t.kind = 'email_confirm'",
        Integer.class,
        email);
  }

  @Test
  void resendResponseTimeDoesNotRevealWhetherTheAccountExists() throws Exception {
    String known = newEmail();
    registerAndGetToken(known);
    for (int i = 0; i < 3; i++) {
      postJson("/api/auth/email/resend", Map.of("email", known), null);
      postJson("/api/auth/email/resend", Map.of("email", newEmail()), null);
    }
    List<Long> knownTimes = new ArrayList<>();
    List<Long> unknownTimes = new ArrayList<>();
    for (int i = 0; i < 9; i++) {
      clock.advance(Duration.ofMinutes(2));
      long t0 = System.nanoTime();
      postJson("/api/auth/email/resend", Map.of("email", known), null);
      knownTimes.add(System.nanoTime() - t0);
      long t1 = System.nanoTime();
      postJson("/api/auth/email/resend", Map.of("email", newEmail()), null);
      unknownTimes.add(System.nanoTime() - t1);
    }
    long diffMs = Math.abs(median(knownTimes) - median(unknownTimes)) / 1_000_000;
    assertThat(diffMs).as("écart de médianes (ms)").isLessThanOrEqualTo(50);
  }

  private static long median(List<Long> values) {
    List<Long> sorted = new ArrayList<>(values);
    sorted.sort(Long::compare);
    return sorted.get(sorted.size() / 2);
  }

  private int change(String email, String password, String newAddress, String ip) throws Exception {
    var req =
        MockMvcRequestBuilders.post("/api/auth/email/change")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("email", email, "password", password, "newEmail", newAddress)))
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                });
    return mvc.perform(req).andReturn().getResponse().getStatus();
  }

  private String currentEmailOf(String idEmail) {
    return jdbc.queryForObject(
        "select email from practitioner where email_normalized = ?", String.class, idEmail);
  }

  @Test
  void changeEmailIsPublic_invalidatesOldTokenAndIssuesNewOne() throws Exception {
    String email = newEmail();
    String newAddress = newEmail();
    String oldToken = registerAndGetToken(email);
    // Limite de renvoi : 1 par minute depuis le jeton d'inscription.
    clock.advance(Duration.ofMinutes(2));

    assertThat(change(email, PASSWORD, newAddress, "198.51.100.1")).isEqualTo(202);

    assertThat(confirm(oldToken)).isEqualTo(410);
    String newToken = mails.confirmationToken(newAddress);
    assertThat(newToken).isNotNull();
    assertThat(confirm(newToken)).isEqualTo(204);
    assertThat(currentEmailOf(newAddress)).isEqualTo(newAddress);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from practitioner where email_normalized = ?",
                Integer.class,
                email))
        .isZero();
    assertThat(
            postJson(
                    "/api/auth/login",
                    Map.of("email", email, "password", PASSWORD, "rememberMe", false),
                    null)
                .getResponse()
                .getStatus())
        .isEqualTo(401);
  }

  @Test
  void changeEmailWithWrongPasswordOrUnknownAccountIs202AndChangesNothing() throws Exception {
    String email = newEmail();
    registerAndGetToken(email);
    clock.advance(Duration.ofMinutes(2));
    String other = newEmail();
    assertThat(change(email, "Mauvais-Mot-De-Passe-1", other, "198.51.100.2")).isEqualTo(202);
    assertThat(change(newEmail(), PASSWORD, other, "198.51.100.3")).isEqualTo(202);
    assertThat(currentEmailOf(email)).isEqualTo(email);
    assertThat(mails.confirmationToken(other)).isNull();
  }

  @Test
  void fiveWrongPasswordsOnChangeEmailLockWith423_andTheLockIsSharedWithLogin() throws Exception {
    String email = newEmail();
    registerAndGetToken(email);
    String ip = "198.51.100.4";
    for (int i = 0; i < 4; i++) {
      assertThat(change(email, "Mauvais-Mot-De-Passe-1", newEmail(), ip)).isEqualTo(202);
    }
    assertThat(change(email, "Mauvais-Mot-De-Passe-1", newEmail(), ip)).isEqualTo(423);
    assertThat(change(email, PASSWORD, newEmail(), ip)).isEqualTo(423);
  }

  @Test
  void changeEmailIsIgnoredOnceConfirmed() throws Exception {
    String email = newEmail();
    String token = registerAndGetToken(email);
    confirm(token);
    clock.advance(Duration.ofMinutes(2));
    String other = newEmail();
    assertThat(change(email, PASSWORD, other, "198.51.100.5")).isEqualTo(202);
    assertThat(currentEmailOf(email)).isEqualTo(email);
    assertThat(mails.confirmationToken(other)).isNull();
  }

  @Test
  void changeEmailIsLimitedToOnePerMinute() throws Exception {
    String email = newEmail();
    registerAndGetToken(email);
    String first = newEmail();
    String second = newEmail();
    // Le jeton d'inscription date de moins d'une minute : rien ne change.
    assertThat(change(email, PASSWORD, first, "198.51.100.6")).isEqualTo(202);
    assertThat(currentEmailOf(email)).isEqualTo(email);
    clock.advance(Duration.ofMinutes(2));
    assertThat(change(email, PASSWORD, first, "198.51.100.6")).isEqualTo(202);
    assertThat(currentEmailOf(first)).isEqualTo(first);
    assertThat(change(first, PASSWORD, second, "198.51.100.6")).isEqualTo(202);
    assertThat(currentEmailOf(first)).isEqualTo(first);
  }

  @Test
  void changeEmailToAnExistingAddressChangesNothingAndStaysSilent() throws Exception {
    String email = newEmail();
    String other = newEmail();
    registerAndGetToken(email);
    registerAndGetToken(other);
    clock.advance(Duration.ofMinutes(2));
    assertThat(change(email, PASSWORD, other, "198.51.100.7")).isEqualTo(202);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from practitioner where email_normalized = ?",
                Integer.class,
                email))
        .isEqualTo(1);
    assertThat(mails.lastText(other, "compte")).isNotNull();
  }
}
