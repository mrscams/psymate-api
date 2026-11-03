package com.psymate.identity.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.identity.admin.bootstrap.BootstrapTestConfig.ExitRecorder;
import com.psymate.identity.admin.bootstrap.BootstrapTestConfig.MutableClock;
import com.psymate.testsupport.TestAdmins;
import com.psymate.testsupport.TotpGenerator;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** M7-08 : création du premier administrateur, premier login, changement de mot de passe, 2FA. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BootstrapTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapIT {

  private static final Pattern PASSWORD_LINE = Pattern.compile("Mot de passe : (\\S+)");
  private static final String NEW_PASSWORD = "Zebre-Tournesol-42";

  @Autowired AdminBootstrapRunner runner;
  @Autowired ExitRecorder exits;
  @Autowired MutableClock clock;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  @Autowired MockMvc mvc;
  private final JsonMapper json = JsonMapper.builder().build();

  /** Adresses des administrateurs créés par le test, fermés à la fin (TestAdmins). */
  private final List<String> created = new ArrayList<>();

  @BeforeEach
  void clearExits() {
    exits.codes.clear();
  }

  @AfterEach
  void closeCreatedAdmins() {
    created.forEach(email -> TestAdmins.closeByEmail(jdbc, email));
  }

  private static String newEmail() {
    return "boot" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
  }

  private void create(String email) {
    created.add(email);
    runner.run(new DefaultApplicationArguments("--create-admin=" + email));
  }

  /**
   * Administrateurs en base. Le lanceur ne crée que des administrateurs, et seuls les tests en
   * créent, l'un après l'autre : aucun listener ni planificateur n'écrit dans {@code practitioner}.
   */
  private int admins() {
    return jdbc.queryForObject(
        "select count(*) from practitioner where role = 'admin'", Integer.class);
  }

  private int rows(String email) {
    return jdbc.queryForObject(
        "select count(*) from practitioner where email_normalized = ?", Integer.class, email);
  }

  private String hashOf(String email) {
    return jdbc.queryForObject(
        "select password_hash from practitioner where email_normalized = ?", String.class, email);
  }

  private static String passwordIn(String out) {
    Matcher m = PASSWORD_LINE.matcher(out);
    assertThat(m.find()).as("ligne Mot de passe dans la sortie").isTrue();
    return m.group(1);
  }

  private MvcResult post(String url, Map<String, Object> body, Cookie cookie) throws Exception {
    var req =
        MockMvcRequestBuilders.post(url)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body));
    if (cookie != null) {
      req.cookie(cookie);
    }
    return mvc.perform(req).andReturn();
  }

  private MvcResult login(String email, String password, String otp) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", email);
    body.put("password", password);
    if (otp != null) {
      body.put("otp", otp);
    }
    return post("/api/auth/login", body, null);
  }

  private MvcResult adminList(Cookie cookie) throws Exception {
    return mvc.perform(MockMvcRequestBuilders.get("/api/admin/verifications").cookie(cookie))
        .andReturn();
  }

  private JsonNode me(Cookie cookie) throws Exception {
    MvcResult r =
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").cookie(cookie)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return body(r);
  }

  private JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private static Map<String, Object> changeBody(String email, String current, String next) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", email);
    body.put("currentPassword", current);
    body.put("newPassword", next);
    return body;
  }

  @Test
  void createsAnApprovedAdminAndPrintsTheOneTimePasswordOnce(CapturedOutput output) {
    String email = newEmail();

    create(email);

    assertThat(exits.codes).containsExactly(0);
    Map<String, Object> row =
        jdbc.queryForMap(
            "select role, verification_status, email_confirmed_at, verified_at, profession,"
                + " display_name, password_hash, preferences::text as prefs, totp_enabled_at,"
                + " tenant_id from practitioner where email_normalized = ?",
            email);
    assertThat(row.get("role")).isEqualTo("admin");
    assertThat(row.get("verification_status")).isEqualTo("approved");
    assertThat(row.get("email_confirmed_at")).isNotNull();
    assertThat(row.get("verified_at")).isNotNull();
    assertThat(row.get("profession")).isEqualTo("psychologist");
    assertThat(row.get("display_name")).isEqualTo("Administrateur");
    assertThat(row.get("totp_enabled_at")).isNull();
    assertThat((String) row.get("prefs")).contains("security.mustChangePassword").contains("true");
    assertThat(
            jdbc.queryForObject(
                "select name from tenant where id = ?", String.class, row.get("tenant_id")))
        .isEqualTo(email);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_event e join practitioner p on p.id ="
                    + " e.practitioner_id where p.email_normalized = ? and e.kind ="
                    + " 'admin_bootstrap'",
                Integer.class,
                email))
        .isEqualTo(1);

    String out = output.getOut();
    assertThat(out).contains(email);
    String password = passwordIn(out);
    assertThat(password).hasSize(20).matches("[A-Za-z0-9]{20}");
    // Affiché une seule fois, jamais stocké en clair, haché Argon2id.
    assertThat(out.split(Pattern.quote(password), -1)).hasSize(2);
    String hash = (String) row.get("password_hash");
    assertThat(hash).startsWith("$argon2").doesNotContain(password);
    assertThat(encoder.matches(password, hash)).isTrue();
    assertThat(out).contains("une seule fois").contains("2FA");
    assertThat(output.getErr()).doesNotContain(password);
  }

  @Test
  void secondRunWithTheSameEmailRefusesAndCreatesNoDuplicate(CapturedOutput output) {
    String email = newEmail();
    create(email);
    String password = passwordIn(output.getOut());
    exits.codes.clear();

    runner.run(new DefaultApplicationArguments("--create-admin=" + email.toUpperCase()));

    assertThat(exits.codes).containsExactly(1);
    assertThat(rows(email)).isEqualTo(1);
    assertThat(output.getErr()).contains("existe déjà").contains(email).contains("aucun doublon");
    // Le mot de passe d'origine n'est pas renvoyé et aucun autre n'est généré.
    assertThat(output.getOut().split(Pattern.quote(password), -1)).hasSize(2);
    assertThat(output.getOut().split("Mot de passe :", -1)).hasSize(2);
    assertThat(encoder.matches(password, hashOf(email))).isTrue();
  }

  @Test
  void firstLoginRequiresPasswordChangeThenTwoFactorBeforeAdminRoutes(CapturedOutput output)
      throws Exception {
    String email = newEmail();
    create(email);
    String oneTime = passwordIn(output.getOut());

    // 1. Le login est refusé tant que le mot de passe n'est pas changé, sans session.
    MvcResult first = login(email, oneTime, null);
    assertThat(first.getResponse().getStatus()).isEqualTo(403);
    assertThat(body(first).path("code").asString()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
    assertThat(first.getResponse().getCookie("SESSION")).isNull();

    // 2. Changement : mauvais mot de passe actuel 401, mot de passe identique 422, valide 204.
    Map<String, Object> wrong = changeBody(email, "Mauvais-mot-de-passe-1", NEW_PASSWORD);
    assertThat(post("/api/auth/password/change", wrong, null).getResponse().getStatus())
        .isEqualTo(401);
    Map<String, Object> same = changeBody(email, oneTime, oneTime);
    MvcResult unchanged = post("/api/auth/password/change", same, null);
    assertThat(unchanged.getResponse().getStatus()).isEqualTo(422);
    assertThat(body(unchanged).path("errors").get(0).path("code").asString())
        .isEqualTo("PASSWORD_UNCHANGED");
    Map<String, Object> ok = changeBody(email, oneTime, NEW_PASSWORD);
    assertThat(post("/api/auth/password/change", ok, null).getResponse().getStatus())
        .isEqualTo(204);
    assertThat(
            jdbc.queryForObject(
                "select preferences::text like '%mustChangePassword%' from practitioner where"
                    + " email_normalized = ?",
                Boolean.class, email))
        .isFalse();

    // 3. L'ancien mot de passe ne marche plus, le nouveau ouvre une session.
    assertThat(login(email, oneTime, null).getResponse().getStatus()).isEqualTo(401);
    MvcResult second = login(email, NEW_PASSWORD, null);
    assertThat(second.getResponse().getStatus()).isEqualTo(200);
    Cookie session = second.getResponse().getCookie("SESSION");
    assertThat(session).isNotNull();
    // M10-03 : /me lit role=admin, passwordChangeRequired=false (levé) et twoFactorEnabled=false.
    JsonNode meBefore2fa = me(session);
    assertThat(meBefore2fa.path("role").asString()).isEqualTo("admin");
    assertThat(meBefore2fa.path("passwordChangeRequired").asBoolean(true)).isFalse();
    assertThat(meBefore2fa.path("twoFactorEnabled").asBoolean(true)).isFalse();
    // Le drapeau du bootstrap, reposé sur la session ouverte, se relit à chaque appel.
    jdbc.update(
        "update practitioner set preferences = preferences ||"
            + " '{\"security.mustChangePassword\": true}'::jsonb where email_normalized = ?",
        email);
    assertThat(me(session).path("passwordChangeRequired").asBoolean(false)).isTrue();
    jdbc.update(
        "update practitioner set preferences = preferences - 'security.mustChangePassword' where"
            + " email_normalized = ?",
        email);
    assertThat(me(session).path("passwordChangeRequired").asBoolean(true)).isFalse();

    // 4. Sans 2FA active, /api/admin/** est refusé.
    MvcResult refused = adminList(session);
    assertThat(refused.getResponse().getStatus()).isEqualTo(403);
    assertThat(body(refused).path("code").asString()).isEqualTo("TWO_FACTOR_REQUIRED");

    // 5. Activation de la 2FA.
    MvcResult enable = post("/api/me/2fa/enable", Map.of(), session);
    assertThat(enable.getResponse().getStatus()).isEqualTo(200);
    String secret = body(enable).path("secret").asString();
    MvcResult verify =
        post(
            "/api/me/2fa/verify",
            Map.of("code", TotpGenerator.code(secret, clock.instant())),
            session);
    assertThat(verify.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(verify).path("recoveryCodes")).hasSize(10);
    // Le code de /verify est consommé : on passe au pas suivant pour obtenir un code neuf.
    clock.advance(Duration.ofSeconds(30));

    assertThat(me(session).path("twoFactorEnabled").asBoolean(false)).isTrue();

    // 6. Le login demande désormais le code, puis /api/admin/** répond.
    MvcResult needOtp = login(email, NEW_PASSWORD, null);
    assertThat(needOtp.getResponse().getStatus()).isEqualTo(202);
    assertThat(body(needOtp).path("otpRequired").asBoolean()).isTrue();
    MvcResult withOtp = login(email, NEW_PASSWORD, TotpGenerator.code(secret, clock.instant()));
    assertThat(withOtp.getResponse().getStatus()).isEqualTo(200);
    MvcResult allowed = adminList(withOtp.getResponse().getCookie("SESSION"));
    assertThat(allowed.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void runnerDoesNothingWithoutTheOption(CapturedOutput output) {
    int before = admins();

    runner.run(new DefaultApplicationArguments());
    runner.run(new DefaultApplicationArguments("--spring.profiles.active=test", "autre"));

    assertThat(exits.codes).isEmpty();
    assertThat(admins()).isEqualTo(before);
    assertThat(rows("autre")).isZero();
    assertThat(output.getOut()).doesNotContain("Mot de passe :");
  }

  @Test
  void invalidEmailExitsWithOneAndCreatesNothing(CapturedOutput output) {
    int before = admins();

    runner.run(new DefaultApplicationArguments("--create-admin=pas-un-email"));
    runner.run(new DefaultApplicationArguments("--create-admin"));

    assertThat(exits.codes).containsExactly(1, 1);
    assertThat(admins()).isEqualTo(before);
    assertThat(rows("pas-un-email")).isZero();
    assertThat(rows("")).isZero();
    assertThat(output.getErr()).contains("invalide");
    assertThat(output.getOut()).doesNotContain("Mot de passe :");
  }
}
