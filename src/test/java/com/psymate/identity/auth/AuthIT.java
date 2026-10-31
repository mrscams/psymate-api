package com.psymate.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.auth.AuthTestConfig.MutableClock;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthTestConfig.class)
class AuthIT {

  private static final String PASSWORD = "Zebre-Tournesol-42";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;
  @Autowired private GenericContainer<?> mailpit;
  @Autowired private PractitionerFacade facade;
  private final JsonMapper json = JsonMapper.builder().build();
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void resetClock() {
    clock.reset();
  }

  private static String newEmail() {
    return "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
  }

  private MvcResult signup(String email, String password, String profession) throws Exception {
    String body =
        json.writeValueAsString(RegistrationFixtures.payload(email, password, profession));
    return mvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andReturn();
  }

  private MvcResult login(String email, String password, boolean rememberMe) throws Exception {
    String body =
        json.writeValueAsString(
            Map.of("email", email, "password", password, "rememberMe", rememberMe));
    return mvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andReturn();
  }

  private MvcResult jsonPost(String url, Map<String, Object> payload) throws Exception {
    return mvc.perform(
            post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)))
        .andReturn();
  }

  private String mailpitGet(String path) throws Exception {
    URI uri = URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path);
    return http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString())
        .body();
  }

  /**
   * Corps texte du dernier e-mail de réinitialisation reçu par l'adresse, ou null (envoi
   * asynchrone).
   */
  private String lastMailText(String to) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (true) {
      JsonNode found =
          json.readTree(
              mailpitGet(
                  "/api/v1/search?query="
                      + URLEncoder.encode(
                          "to:" + to + " subject:\"passe\"", StandardCharsets.UTF_8)));
      if (!found.path("messages").isEmpty()) {
        String id = found.path("messages").get(0).path("ID").asString();
        return json.readTree(mailpitGet("/api/v1/message/" + id)).path("Text").asString();
      }
      if (System.nanoTime() > deadline) {
        return null;
      }
      Thread.sleep(100);
    }
  }

  private String resetTokenFromMail(String to) throws Exception {
    String text = lastMailText(to);
    assertThat(text).as("reset e-mail received in Mailpit").isNotNull();
    Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(text);
    assertThat(m.find()).isTrue();
    return m.group(1);
  }

  @Test
  void signupThenLoginReturnCookie_andProtectedRouteRequiresSession() throws Exception {
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

    String email = newEmail();
    assertThat(signup(email, PASSWORD, "psychologist").getResponse().getStatus()).isEqualTo(201);

    MvcResult login = login(email, PASSWORD, false);
    assertThat(login.getResponse().getStatus()).isEqualTo(200);
    String setCookie = login.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie).contains("SESSION=").contains("HttpOnly").contains("Secure");
    assertThat(setCookie).containsIgnoringCase("SameSite=Lax");

    Cookie session = login.getResponse().getCookie("SESSION");
    assertThat(session).isNotNull();
    mvc.perform(get("/api/auth/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.verificationStatus").value("email_pending"))
        .andExpect(jsonPath("$.profession").value("psychologist"));

    var id =
        UUID.fromString(
            json.readTree(login.getResponse().getContentAsString()).path("id").asString());
    assertThat(facade.find(id))
        .get()
        .extracting(PractitionerFacade.PractitionerView::profession)
        .isEqualTo("psychologist");
  }

  @Test
  void logoutInvalidatesSession() throws Exception {
    String email = newEmail();
    signup(email, PASSWORD, "psychologist");
    Cookie session = login(email, PASSWORD, false).getResponse().getCookie("SESSION");
    mvc.perform(post("/api/auth/logout").cookie(session)).andExpect(status().isNoContent());
    mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void unauthenticatedErrorIsProblemJson() throws Exception {
    mvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void signupCreatesTenantAndDefaultPreferencesPerProfession() throws Exception {
    String coach = newEmail();
    String psychiatrist = newEmail();
    String psychologist = newEmail();
    signup(coach, PASSWORD, "coach");
    signup(psychiatrist, PASSWORD, "psychiatrist");
    signup(psychologist, PASSWORD, "psychologist");

    assertThat(json.readTree(prefs(coach)))
        .isEqualTo(
            json.readTree(
                "{\"vocabulary.subjectLabel\":\"client\",\"entryTypes.medicationVisible\":false}"));
    assertThat(json.readTree(prefs(psychiatrist)))
        .isEqualTo(json.readTree("{\"entryTypes.medicationVisible\":true}"));
    assertThat(json.readTree(prefs(psychologist))).isEqualTo(json.readTree("{}"));

    Map<String, Object> row =
        jdbc.queryForMap(
            "select p.verification_status, p.display_name, p.password_hash, p.tenant_id, t.name"
                + " from practitioner p join tenant t on t.id = p.tenant_id where"
                + " p.email_normalized = ?",
            psychiatrist);
    assertThat(row.get("verification_status")).isEqualTo("email_pending");
    assertThat(row.get("display_name")).isEqualTo("Dr Maya Kaddour");
    assertThat((String) row.get("password_hash")).startsWith("$argon2id$").doesNotContain(PASSWORD);
    assertThat(
            jdbc.queryForObject(
                "select display_name from practitioner where email_normalized = ?",
                String.class,
                psychologist))
        .isEqualTo("Maya Kaddour");
  }

  private String prefs(String email) {
    return jdbc.queryForObject(
        "select preferences::text from practitioner where email_normalized = ?",
        String.class,
        email);
  }

  @Test
  void passwordIsHashedWithArgon2id() throws Exception {
    String email = newEmail();
    signup(email, PASSWORD, "psychologist");
    String hash =
        jdbc.queryForObject(
            "select password_hash from practitioner where email_normalized = ?",
            String.class,
            email);
    assertThat(hash).contains("$argon2id$");
  }

  @Test
  void failedLoginOnUnknownEmailAndWrongPasswordHaveSameCodeAndBody() throws Exception {
    String email = newEmail();
    signup(email, PASSWORD, "psychologist");

    MvcResult unknown = login(newEmail(), PASSWORD, false);
    MvcResult wrong = login(email, "Mauvais-Mot-De-Passe-1", false);

    assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
    assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
    assertThat(unknown.getResponse().getContentAsString())
        .isEqualTo(wrong.getResponse().getContentAsString());
    assertThat(unknown.getResponse().getContentType()).contains("application/problem+json");
    assertThat(unknown.getResponse().getCookie("SESSION")).isNull();
  }

  @Test
  void motdepasseIsRefusedWith422() throws Exception {
    MvcResult r = signup(newEmail(), "motdepasse", "psychologist");
    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
    JsonNode body = json.readTree(r.getResponse().getContentAsString());
    List<String> codes =
        body.path("errors").valueStream().map(e -> e.path("code").asString()).toList();
    assertThat(codes).contains("PASSWORD_TOO_SHORT");
    assertThat(body.path("errors").valueStream().map(e -> e.path("field").asString()))
        .containsOnly("password");
  }

  @Test
  void elevenCharacterPasswordIsRefusedWith422() throws Exception {
    MvcResult r = signup(newEmail(), "Motdepasse1", "psychologist");
    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
    JsonNode body = json.readTree(r.getResponse().getContentAsString());
    assertThat(body.path("errors").get(0).path("field").asString()).isEqualTo("password");
    assertThat(body.path("errors").get(0).path("code").asString()).isEqualTo("PASSWORD_TOO_SHORT");
  }

  @Test
  void invalidFieldsGive422WithErrorsPerField() throws Exception {
    MvcResult r =
        jsonPost(
            "/api/auth/signup",
            Map.of(
                "email",
                "pas-un-email",
                "password",
                PASSWORD,
                "firstName",
                "A",
                "lastName",
                "B",
                "profession",
                "nurse"));
    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    JsonNode errors = json.readTree(r.getResponse().getContentAsString()).path("errors");
    List<String> fields = errors.valueStream().map(e -> e.path("field").asString()).toList();
    assertThat(fields).contains("email", "profession");
  }

  @Test
  void alreadyRegisteredEmailGivesSame201AndCreatesNoSecondAccount() throws Exception {
    String email = newEmail();
    MvcResult first = signup(email, PASSWORD, "psychologist");
    MvcResult dup = signup(email.toUpperCase(), PASSWORD, "coach");
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    assertThat(dup.getResponse().getStatus()).isEqualTo(201);
    assertThat(dup.getResponse().getContentAsString())
        .isEqualTo(first.getResponse().getContentAsString());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from practitioner where email_normalized = ?",
                Integer.class,
                email))
        .isEqualTo(1);
  }

  @Test
  void rememberMeExtendsSessionTo30Days() throws Exception {
    String email = newEmail();
    signup(email, PASSWORD, "psychologist");
    String normal = login(email, PASSWORD, false).getResponse().getCookie("SESSION").getValue();
    String remembered = login(email, PASSWORD, true).getResponse().getCookie("SESSION").getValue();
    assertThat(maxInactive(normal)).isEqualTo(Duration.ofHours(12).toSeconds());
    assertThat(maxInactive(remembered)).isEqualTo(Duration.ofDays(30).toSeconds());
  }

  @Test
  void rememberMeCookieHasThirtyDayMaxAge_otherwiseBrowserSessionCookie() throws Exception {
    String email = newEmail();
    signup(email, PASSWORD, "psychologist");
    Cookie normal = login(email, PASSWORD, false).getResponse().getCookie("SESSION");
    Cookie remembered = login(email, PASSWORD, true).getResponse().getCookie("SESSION");
    assertThat(normal.getMaxAge()).isEqualTo(-1);
    assertThat(remembered.getMaxAge()).isEqualTo((int) Duration.ofDays(30).toSeconds());
    assertThat(remembered.isHttpOnly()).isTrue();
    assertThat(remembered.getSecure()).isTrue();
  }

  @Test
  void passwordResetConfirmRevokesExistingSessions() throws Exception {
    String email = newEmail();
    signup(email, PASSWORD, "psychologist");
    Cookie session = login(email, PASSWORD, true).getResponse().getCookie("SESSION");
    mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());

    jsonPost("/api/auth/password-reset/request", Map.of("email", email));
    String token = resetTokenFromMail(email);
    assertThat(
            jsonPost(
                    "/api/auth/password-reset/confirm",
                    Map.of("token", token, "password", "Nouveau-Secret-2027"))
                .getResponse()
                .getStatus())
        .isEqualTo(204);

    mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
  }

  private long maxInactive(String cookieValue) {
    // Le cookie Spring Session est l'identifiant encodé en base64.
    String sessionId =
        new String(java.util.Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
    return jdbc.queryForObject(
        "select max_inactive_interval from spring_session where session_id = ?",
        Long.class,
        sessionId);
  }

  @Test
  void passwordResetEmailIsInMailpit_tokenIsHashed_andConsumedOnlyOnce() throws Exception {
    String email = newEmail();
    signup(email, PASSWORD, "psychologist");

    assertThat(
            jsonPost("/api/auth/password-reset/request", Map.of("email", email))
                .getResponse()
                .getStatus())
        .isEqualTo(202);

    String text = lastMailText(email);
    assertThat(text).contains("PsyMate");
    String token = resetTokenFromMail(email);

    // Le jeton clair n'est pas en base, seul son SHA-256 l'est.
    Map<String, Object> row =
        jdbc.queryForMap(
            "select token_hash, kind, expires_at, consumed_at from auth_token t join practitioner"
                + " p on p.id = t.practitioner_id where p.email_normalized = ? and t.kind ="
                + " 'password_reset'",
            email);
    assertThat(row.get("kind")).isEqualTo("password_reset");
    assertThat(row.get("token_hash")).isEqualTo(AuthService.sha256(token)).isNotEqualTo(token);
    assertThat(row.get("consumed_at")).isNull();

    String newPassword = "Nouveau-Secret-2027";
    assertThat(
            jsonPost(
                    "/api/auth/password-reset/confirm",
                    Map.of("token", token, "password", newPassword))
                .getResponse()
                .getStatus())
        .isEqualTo(204);
    assertThat(
            jdbc.queryForObject(
                "select consumed_at is not null from auth_token where token_hash = ?",
                Boolean.class,
                AuthService.sha256(token)))
        .isTrue();

    // Second usage : refusé.
    assertThat(
            jsonPost(
                    "/api/auth/password-reset/confirm",
                    Map.of("token", token, "password", "Autre-Secret-2028"))
                .getResponse()
                .getStatus())
        .isEqualTo(410);

    assertThat(login(email, newPassword, false).getResponse().getStatus()).isEqualTo(200);
    assertThat(login(email, PASSWORD, false).getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void passwordResetTokenExpiresAfter30Minutes() throws Exception {
    String email = newEmail();
    signup(email, PASSWORD, "psychologist");
    jsonPost("/api/auth/password-reset/request", Map.of("email", email));
    String token = resetTokenFromMail(email);

    clock.advance(Duration.ofMinutes(31));

    MvcResult r =
        jsonPost(
            "/api/auth/password-reset/confirm",
            Map.of("token", token, "password", "Nouveau-Secret-2027"));
    assertThat(r.getResponse().getStatus()).isEqualTo(410);
    assertThat(login(email, PASSWORD, false).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void passwordResetTokenStillValidJustBefore30Minutes() throws Exception {
    String email = newEmail();
    signup(email, PASSWORD, "psychologist");
    jsonPost("/api/auth/password-reset/request", Map.of("email", email));
    String token = resetTokenFromMail(email);

    clock.advance(Duration.ofMinutes(29));

    assertThat(
            jsonPost(
                    "/api/auth/password-reset/confirm",
                    Map.of("token", token, "password", "Nouveau-Secret-2027"))
                .getResponse()
                .getStatus())
        .isEqualTo(204);
  }

  @Test
  void passwordResetConfirmAppliesPolicyAndKeepsTokenUsable() throws Exception {
    String email = newEmail();
    signup(email, PASSWORD, "psychologist");
    jsonPost("/api/auth/password-reset/request", Map.of("email", email));
    String token = resetTokenFromMail(email);

    assertThat(
            jsonPost(
                    "/api/auth/password-reset/confirm", Map.of("token", token, "password", "court"))
                .getResponse()
                .getStatus())
        .isEqualTo(422);
    assertThat(
            jsonPost(
                    "/api/auth/password-reset/confirm",
                    Map.of("token", token, "password", "Nouveau-Secret-2027"))
                .getResponse()
                .getStatus())
        .isEqualTo(204);
  }

  @Test
  void passwordResetRequestForUnknownEmailIs202AndSendsNothing() throws Exception {
    String email = newEmail();
    assertThat(
            jsonPost("/api/auth/password-reset/request", Map.of("email", email))
                .getResponse()
                .getStatus())
        .isEqualTo(202);
    assertThat(lastMailText(email)).isNull();
    assertThat(jdbc.queryForObject("select count(*) from auth_token", Integer.class)).isNotNull();
  }

  @Test
  void newResetRequestInvalidatesThePreviousToken() throws Exception {
    String email = newEmail();
    signup(email, PASSWORD, "psychologist");
    jsonPost("/api/auth/password-reset/request", Map.of("email", email));
    String first = resetTokenFromMail(email);
    jsonPost("/api/auth/password-reset/request", Map.of("email", email));

    assertThat(
            jsonPost(
                    "/api/auth/password-reset/confirm",
                    Map.of("token", first, "password", "Nouveau-Secret-2027"))
                .getResponse()
                .getStatus())
        .isEqualTo(410);
  }
}
