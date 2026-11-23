package com.psymate.directory.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertArabicGetsFrench;
import static com.psymate.testsupport.ArabicFallback.frenchFallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.MailpitClient;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import com.psymate.testsupport.TestAdmins;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M16-02 (G Q96, Q100) : annuaire en arabe. Le relais du formulaire de contact garde sa source de
 * langue (préférence {@code locale} du praticien) et part en français pour un praticien en {@code
 * ar} ; l'administration du vocabulaire et la liste publique répondent en français à une requête en
 * arabe, sans erreur. Même configuration que ContactRequestIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, SharedMailpit.class})
class DirectoryArabicIT {

  private static final AtomicInteger IP = new AtomicInteger();
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;

  @BeforeEach
  void setUp() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  @AfterEach
  void closeTestAdmins() {
    TestAdmins.closeInTenant(jdbc, tenant);
  }

  private static String freshIp() {
    int n = IP.incrementAndGet();
    return "10.16." + (n / 250) + "." + (n % 250 + 1);
  }

  private UUID account(String role, boolean totp, String preferences) {
    UUID id = UUID.randomUUID();
    String email = "compte-" + id.toString().substring(0, 8) + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, email_confirmed_at,"
            + " password_hash, first_name, last_name, display_name, profession,"
            + " verification_status, role, totp_secret_enc, totp_enabled_at, preferences)"
            + " values (?, ?, ?, ?, now(), 'x', 'T', 'P', 'Dr Annuaire', 'psychologist',"
            + " 'approved', ?, ?, ?, cast(? as jsonb))",
        id,
        tenant,
        email,
        email,
        role,
        totp ? new byte[] {1} : null,
        totp ? Timestamp.from(Instant.now()) : null,
        preferences);
    return id;
  }

  private String emailOf(UUID id) {
    return jdbc.queryForObject("select email from practitioner where id = ?", String.class, id);
  }

  private ResultActions asAdmin(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      UUID admin,
      String language) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            admin, tenant, emailOf(admin), "Dr Test", "psychologist", "approved");
    try {
      return mvc.perform(
          request
              .header("Accept-Language", language)
              .with(
                  authentication(
                      UsernamePasswordAuthenticationToken.authenticated(
                          principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void contactRelayToAnArabicPractitionerIsSent() throws Exception {
    UUID id = account("practitioner", false, "{\"locale\":\"ar\"}");
    String slug = "contact-ar-" + id.toString().substring(0, 8);
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, display_name, published)"
            + " values (?, ?, 'Dr Annuaire', true)",
        id,
        slug);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Camille Visiteur");
    body.put("email", "visiteur-" + UUID.randomUUID() + "@example.test");
    body.put("subject", "Première consultation");
    body.put("message", "Bonjour, je souhaite prendre rendez-vous.");
    body.put("consent", true);
    body.put("website", "");
    body.put("formShownAt", Instant.now().minusSeconds(30).toString());
    String ip = freshIp();

    mvc.perform(
            post("/api/public/directory/practitioners/" + slug + "/contact")
                .with(
                    r -> {
                      r.setRemoteAddr(ip);
                      return r;
                    })
                .header("Accept-Language", ARABIC)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)))
        .andExpect(status().isAccepted());

    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    Map<String, Object> row;
    do {
      Thread.sleep(100);
      row = jdbc.queryForMap("select * from contact_request where practitioner_id = ?", id);
    } while (row.get("relayed_at") == null
        && row.get("relay_error") == null
        && System.nanoTime() < deadline);
    assertThat(row.get("relay_error")).isNull();
    assertThat(row.get("relayed_at")).isNotNull();

    JsonNode mail = new MailpitClient(SharedMailpit.container()).awaitMessage(emailOf(id));
    assertThat(mail).as("e-mail relayé").isNotNull();
    if (frenchFallback()) {
      assertThat(mail.path("Subject").asString()).contains("nouveau message de");
    }
  }

  @Test
  void vocabularyAdministrationErrorsInArabicAreTheFrenchOnes() throws Exception {
    UUID admin = account("admin", true, "{}");
    String bad =
        JSON.writeValueAsString(Map.of("kind", "specialty", "code", "BAD", "labelFr", "x"));
    assertArabicGetsFrench(
        asAdmin(
                post("/api/admin/vocabulary/directory")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bad),
                admin,
                "fr")
            .andExpect(status().is4xxClientError()),
        asAdmin(
            post("/api/admin/vocabulary/directory")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bad),
            admin,
            ARABIC));
    assertArabicGetsFrench(
        asAdmin(
                post("/api/admin/vocabulary/directory")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{nope"),
                admin,
                "fr")
            .andExpect(status().isBadRequest()),
        asAdmin(
            post("/api/admin/vocabulary/directory")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{nope"),
            admin,
            ARABIC));
  }

  @Test
  void thePublicVocabularyAnswersAnArabicRequest() throws Exception {
    String ip = freshIp();
    assertArabicGetsFrench(
        mvc.perform(
                get("/api/public/directory/vocabulary")
                    .with(
                        r -> {
                          r.setRemoteAddr(ip);
                          return r;
                        })
                    .header("Accept-Language", "fr"))
            .andExpect(status().isOk()),
        mvc.perform(
            get("/api/public/directory/vocabulary")
                .with(
                    r -> {
                      r.setRemoteAddr(ip);
                      return r;
                    })
                .header("Accept-Language", ARABIC)));
  }
}
