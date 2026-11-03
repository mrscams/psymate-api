package com.psymate.identity.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.identity.auth.AbstractLoginIT;
import com.psymate.identity.auth.RegistrationFixtures;
import com.psymate.identity.auth.dto.MeResponse;
import com.psymate.testsupport.TestAdmins;
import com.psymate.testsupport.TotpGenerator;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

/**
 * M10-03 : GET /api/auth/me expose role, twoFactorEnabled et passwordChangeRequired, relus en base
 * à chaque appel ; aucun des trois n'existe en écriture (A11).
 */
class AuthMeIT extends AbstractLoginIT {

  private static final Set<String> NEW_FIELDS =
      Set.of("role", "twoFactorEnabled", "passwordChangeRequired");
  private static final String NEW_PASSWORD = "Nouveau-Tournesol-77";

  /** Comptes promus administrateur par le test, fermés à la fin (TestAdmins). */
  private final List<String> promoted = new java.util.ArrayList<>();

  @AfterEach
  void closePromotedAdmins() {
    promoted.forEach(email -> TestAdmins.closeByEmail(jdbc, email));
  }

  private JsonNode me(Cookie session) throws Exception {
    MvcResult r =
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").cookie(session)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return body(r);
  }

  private void requirePasswordChange(String email) {
    jdbc.update(
        "update practitioner set preferences = preferences ||"
            + " '{\"security.mustChangePassword\": true}'::jsonb where email_normalized = ?",
        email);
  }

  private String code(String secret) {
    return TotpGenerator.code(secret, clock.instant());
  }

  @Test
  void practitionerReadsRolePractitionerAndNoTwoFactorNoPasswordChange() throws Exception {
    String email = account();
    MvcResult login = login(email, PASSWORD, null, IP);
    assertThat(login.getResponse().getStatus()).isEqualTo(200);

    // La réponse de connexion porte les mêmes champs que GET /api/auth/me.
    JsonNode fromLogin = body(login);
    assertThat(fromLogin.path("role").asString()).isEqualTo("practitioner");
    assertThat(fromLogin.path("twoFactorEnabled").asBoolean(true)).isFalse();
    assertThat(fromLogin.path("passwordChangeRequired").asBoolean(true)).isFalse();

    JsonNode me = me(login.getResponse().getCookie("SESSION"));
    assertThat(me.path("email").asString()).isEqualTo(email);
    assertThat(me.path("role").asString()).isEqualTo("practitioner");
    assertThat(me.path("twoFactorEnabled").isBoolean()).isTrue();
    assertThat(me.path("twoFactorEnabled").asBoolean()).isFalse();
    assertThat(me.path("passwordChangeRequired").isBoolean()).isTrue();
    assertThat(me.path("passwordChangeRequired").asBoolean()).isFalse();
  }

  @Test
  void roleIsAdminForAnAdministrator() throws Exception {
    String email = account("approved");
    promoted.add(email);
    // Le rôle admin exige une 2FA vérifiée pour /api/admin/** seulement : /me ne l'impose pas.
    jdbc.update("update practitioner set role = 'admin' where email_normalized = ?", email);

    Cookie session = session(email);

    assertThat(me(session).path("role").asString()).isEqualTo("admin");
  }

  @Test
  void twoFactorEnabledFollowsEnableVerifyAndDisable() throws Exception {
    String email = account();
    Cookie session = session(email);
    assertThat(me(session).path("twoFactorEnabled").asBoolean(true)).isFalse();

    String secret = body(post("/api/me/2fa/enable", null, session, IP)).path("secret").asString();
    // En attente : le secret existe mais l'activation n'est pas vérifiée.
    JsonNode pending = me(session);
    assertThat(pending.path("twoFactorEnabled").asBoolean(true)).isFalse();

    MvcResult verify = post("/api/me/2fa/verify", Map.of("code", code(secret)), session, IP);
    assertThat(verify.getResponse().getStatus()).isEqualTo(200);
    JsonNode enabled = me(session);
    assertThat(enabled.path("twoFactorEnabled").asBoolean(false)).isTrue();
    // Le secret et les codes de secours ne sortent jamais par /me.
    assertThat(enabled.toString()).doesNotContain(secret).doesNotContain("recoveryCodes");
    assertThat(enabled.propertyNames()).doesNotContain("secret", "totpSecret", "recoveryCodes");

    // Le code de /verify est consommé : on passe au pas suivant pour obtenir un code neuf.
    clock.advance(Duration.ofSeconds(30));
    MvcResult disable =
        post(
            "/api/me/2fa/disable", Map.of("password", PASSWORD, "code", code(secret)), session, IP);
    assertThat(disable.getResponse().getStatus()).isEqualTo(204);
    assertThat(me(session).path("twoFactorEnabled").asBoolean(true)).isFalse();
  }

  @Test
  void passwordChangeRequiredIsTrueWhileTheFlagIsSetThenFalseAfterPasswordChange()
      throws Exception {
    String email = account();
    Cookie session = session(email);
    assertThat(me(session).path("passwordChangeRequired").asBoolean(true)).isFalse();

    // Obligation de changement posée sur un compte déjà connecté (même clé que le bootstrap).
    requirePasswordChange(email);
    assertThat(me(session).path("passwordChangeRequired").asBoolean(false)).isTrue();

    Map<String, Object> change = new LinkedHashMap<>();
    change.put("email", email);
    change.put("currentPassword", PASSWORD);
    change.put("newPassword", NEW_PASSWORD);
    assertThat(post("/api/auth/password/change", change, null, IP).getResponse().getStatus())
        .isEqualTo(204);

    // Les sessions sont révoquées ; la suivante lit false.
    assertThat(
            mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").cookie(session))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
    MvcResult relogin = login(email, NEW_PASSWORD, null, IP);
    assertThat(relogin.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(relogin).path("passwordChangeRequired").asBoolean(true)).isFalse();
    assertThat(
            me(relogin.getResponse().getCookie("SESSION"))
                .path("passwordChangeRequired")
                .asBoolean(true))
        .isFalse();
  }

  @Test
  void meRequiresASession() throws Exception {
    assertThat(
            mvc.perform(MockMvcRequestBuilders.get("/api/auth/me"))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
  }

  @Test
  void aSignupBodyCarryingTheThreeFieldsIsIgnored() throws Exception {
    String email = newEmail();
    Map<String, Object> payload =
        new LinkedHashMap<>(RegistrationFixtures.payload(email, PASSWORD, "psychologist"));
    payload.put("role", "admin");
    payload.put("twoFactorEnabled", true);
    payload.put("passwordChangeRequired", true);

    // Jackson ignore les propriétés inconnues : l'inscription réussit, sans aucun effet des trois.
    assertThat(post("/api/auth/register", payload, null, IP).getResponse().getStatus())
        .isEqualTo(201);

    Map<String, Object> row =
        jdbc.queryForMap(
            "select role, totp_enabled_at, preferences::text as prefs from practitioner where"
                + " email_normalized = ?",
            email);
    assertThat(row.get("role")).isEqualTo("practitioner");
    assertThat(row.get("totp_enabled_at")).isNull();
    assertThat((String) row.get("prefs")).doesNotContain("mustChangePassword");
    // Le compte est email_pending (e-mail non confirmé, login refusé) : on le passe à docs_pending
    // à la main, comme AbstractLoginIT.account, pour pouvoir ouvrir une session.
    jdbc.update(
        "update practitioner set verification_status = 'docs_pending' where email_normalized = ?",
        email);
    JsonNode me = me(session(email));
    assertThat(me.path("role").asString()).isEqualTo("practitioner");
    assertThat(me.path("twoFactorEnabled").asBoolean(true)).isFalse();
    assertThat(me.path("passwordChangeRequired").asBoolean(true)).isFalse();
  }

  @Test
  void aLoginBodyCarryingTheThreeFieldsIsIgnored() throws Exception {
    String email = account();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", email);
    body.put("password", PASSWORD);
    body.put("role", "admin");
    body.put("twoFactorEnabled", true);
    body.put("passwordChangeRequired", true);

    MvcResult r = post("/api/auth/login", body, null, IP);

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(r).path("role").asString()).isEqualTo("practitioner");
    assertThat(body(r).path("twoFactorEnabled").asBoolean(true)).isFalse();
    assertThat(body(r).path("passwordChangeRequired").asBoolean(true)).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select role from practitioner where email_normalized = ?", String.class, email))
        .isEqualTo("practitioner");
    assertThat(me(r.getResponse().getCookie("SESSION")).path("role").asString())
        .isEqualTo("practitioner");
  }

  @Test
  void noWriteDtoOfTheAuthPackageDeclaresTheThreeFields() {
    // Scan de tous les *Request du package : un futur DTO d'écriture (M10-04) est contrôlé aussi.
    List<Class<?>> requests =
        new ClassFileImporter()
            .importPackages("com.psymate.identity.auth.dto").stream()
                .filter(c -> c.getSimpleName().endsWith("Request"))
                .<Class<?>>map(c -> c.reflect())
                .toList();
    assertThat(requests).hasSizeGreaterThanOrEqualTo(10);
    for (Class<?> request : requests) {
      assertThat(request.isRecord()).as("%s est un record", request.getSimpleName()).isTrue();
      for (RecordComponent component : request.getRecordComponents()) {
        assertThat(NEW_FIELDS)
            .as("%s.%s", request.getSimpleName(), component.getName())
            .doesNotContain(component.getName());
      }
    }
    assertThat(Arrays.stream(MeResponse.class.getRecordComponents()).map(RecordComponent::getName))
        .containsAll(NEW_FIELDS);
  }
}
