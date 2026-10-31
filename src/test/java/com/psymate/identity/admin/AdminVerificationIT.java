package com.psymate.identity.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerFacade;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** M1-14 : validation des praticiens par l'administration, de bout en bout (Postgres, Mailpit). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminTestConfig.class)
class AdminVerificationIT {

  private static final String BASE = "/api/admin/verifications";
  private static final String PASSWORD = "Zebre-Tournesol-42";
  private static final byte[] PDF =
      ("%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n"
              + "contenu confidentiel diplome ".repeat(20))
          .getBytes(StandardCharsets.US_ASCII);

  private static final List<String> FORBIDDEN_KEY_PARTS =
      List.of("patient", "entry", "session", "storagekey", "sha256", "password", "totp");

  /** Dossier de test : praticien et identifiants de ses deux justificatifs. */
  private record Dossier(UUID id, UUID diploma, UUID identity) {}

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AdminTestConfig.Recorder recorder;
  @Autowired private GenericContainer<?> mailpit;
  @Autowired private PractitionerFacade facade;
  @Autowired private AdminVerificationService service;
  private final JsonMapper json = JsonMapper.builder().build();
  private AdminMailpit mails;

  private UUID tenant;

  @BeforeEach
  void setUp() {
    mails = new AdminMailpit(mailpit);
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    // Isolation : les dossiers laissés en attente par les autres tests sortent de la file.
    jdbc.update(
        "update practitioner set verification_status = 'closed' where role <> 'admin'"
            + " and verification_status in ('under_review', 'needs_more')");
  }

  // ---- fixtures

  private UUID insertPractitioner(
      String displayName, String status, String role, boolean totp, String locale) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, email_confirmed_at,"
            + " password_hash, first_name, last_name, display_name, locale, profession, phone,"
            + " professional_number, address_street, address_postal_code, address_city,"
            + " verification_status, role, totp_secret_enc, totp_enabled_at)"
            + " values (?, ?, ?, ?, now(), 'x', 'Maya', 'Kaddour', ?, ?, 'psychologist',"
            + " '06 12 34 56 78', 'ADELI123456', '12 rue des Lilas', '69003', 'Lyon',"
            + " ?, ?, ?, ?)",
        id,
        tenant,
        email,
        email,
        displayName,
        locale,
        status,
        role,
        totp ? new byte[] {1} : null,
        totp ? Timestamp.from(Instant.now()) : null);
    return id;
  }

  private UUID admin() {
    return insertPractitioner("Admin Test", "approved", "admin", true, "fr");
  }

  private UUID adminWithoutTwoFactor() {
    return insertPractitioner("Admin Sans 2FA", "approved", "admin", false, "fr");
  }

  private UUID practitioner(String displayName, String status) {
    return insertPractitioner(displayName, status, "practitioner", false, "fr");
  }

  private void submitted(UUID practitionerId, Instant at) {
    jdbc.update(
        "insert into security_event (id, practitioner_id, kind, occurred_at) values (?, ?,"
            + " 'verification_submitted', ?)",
        UUID.randomUUID(),
        practitionerId,
        Timestamp.from(at));
  }

  /** Praticien sans justificatif, en revue, soumis à la date donnée. */
  private UUID queued(String displayName, String status, Instant submittedAt) {
    UUID id = practitioner(displayName, status);
    if (submittedAt != null) {
      submitted(id, submittedAt);
    }
    return id;
  }

  private RequestPostProcessor as(UUID id, String role, String status) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            id, tenant, id + "@example.test", "Dr Test", "psychologist", status);
    List<SimpleGrantedAuthority> authorities =
        role == null ? List.of() : List.of(new SimpleGrantedAuthority(role));
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
  }

  private RequestPostProcessor asAdmin(UUID id) {
    return as(id, "ROLE_ADMIN", "approved");
  }

  private UUID upload(UUID practitionerId, String type) throws Exception {
    MvcResult r =
        mvc.perform(
                multipart("/api/me/verification/documents")
                    .file(new MockMultipartFile("file", type + ".pdf", "application/pdf", PDF))
                    .param("type", type)
                    .param("title", "Master de psychologie")
                    .param("issuer", "Université de Lyon")
                    .param("year", "2012")
                    .with(as(practitionerId, "ROLE_PRACTITIONER", "docs_pending")))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(201);
    return UUID.fromString(body(r).path("id").asString());
  }

  /** Dossier complet (diplôme et pièce d'identité déposés par l'API praticien), soumis. */
  private Dossier dossierWithDocuments(String status, Instant submittedAt) throws Exception {
    UUID id = practitioner("Dr Claire Moreau", "docs_pending");
    UUID diploma = upload(id, "diploma");
    UUID identity = upload(id, "identity");
    jdbc.update("update practitioner set verification_status = ? where id = ?", status, id);
    submitted(id, submittedAt);
    return new Dossier(id, diploma, identity);
  }

  private JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private MvcResult list(UUID admin, String query) throws Exception {
    return mvc.perform(get(BASE + query).with(asAdmin(admin))).andReturn();
  }

  private MvcResult decide(UUID admin, UUID practitionerId, Map<String, Object> payload)
      throws Exception {
    return mvc.perform(
            post(BASE + "/" + practitionerId + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload))
                .with(asAdmin(admin)))
        .andReturn();
  }

  private MvcResult review(UUID admin, Dossier d, UUID docId, String status) throws Exception {
    return mvc.perform(
            patch(BASE + "/" + d.id() + "/documents/" + docId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reviewedStatus", status)))
                .with(asAdmin(admin)))
        .andReturn();
  }

  private static Map<String, Object> decision(String kind, String message) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("kind", kind);
    if (message != null) {
      m.put("messageToPractitioner", message);
    }
    return m;
  }

  private int status(MvcResult r) {
    return r.getResponse().getStatus();
  }

  private static boolean await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        return false;
      }
      Thread.sleep(50);
    }
    return true;
  }

  private String statusOf(UUID id) {
    return jdbc.queryForObject(
        "select verification_status from practitioner where id = ?", String.class, id);
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  // ---- 1. rôle et 2FA

  @Test
  void practitionerRoleIsRefusedOnAdminRoutes() throws Exception {
    UUID p = practitioner("Dr Test", "approved");
    MvcResult r = mvc.perform(get(BASE).with(as(p, "ROLE_PRACTITIONER", "approved"))).andReturn();
    assertThat(status(r)).isEqualTo(403);
    assertThat(body(r).path("code").asString()).isEqualTo("ADMIN_ROLE_REQUIRED");
    assertThat(r.getResponse().getContentType()).contains("application/problem+json");

    MvcResult noAuthority = mvc.perform(get(BASE).with(as(p, null, "approved"))).andReturn();
    assertThat(status(noAuthority)).isEqualTo(403);
    assertThat(body(noAuthority).path("code").asString()).isEqualTo("ADMIN_ROLE_REQUIRED");
  }

  @Test
  void unauthenticatedRequestIsRefusedWith401() throws Exception {
    assertThat(status(mvc.perform(get(BASE)).andReturn())).isEqualTo(401);
  }

  @Test
  void adminWithoutTwoFactorIsRefusedWithTwoFactorRequired() throws Exception {
    MvcResult r = list(adminWithoutTwoFactor(), "");
    assertThat(status(r)).isEqualTo(403);
    assertThat(body(r).path("code").asString()).isEqualTo("TWO_FACTOR_REQUIRED");
  }

  @Test
  void adminWithTwoFactorGetsTheQueue() throws Exception {
    assertThat(status(list(admin(), ""))).isEqualTo(200);
  }

  // ---- 2. file d'attente

  @Test
  void queueIsOrderedOldestFirstAndFlagsOverdueDossiers() throws Exception {
    Instant now = Instant.now();
    // Insertion volontairement dans l'ordre inverse du tri attendu.
    UUID recent = queued("Dr Recent", "under_review", now.minus(Duration.ofHours(1)));
    UUID old = queued("Dr Ancien", "under_review", now.minus(Duration.ofDays(5)));
    jdbc.update(
        "insert into verification_document (id, practitioner_id, type, title, storage_key, mime,"
            + " size_bytes, sha256) values (?, ?, 'diploma', 'Master', 'k', 'application/pdf', 10,"
            + " 'h')",
        UUID.randomUUID(),
        old);

    MvcResult r = list(admin(), "");
    assertThat(status(r)).isEqualTo(200);
    JsonNode items = body(r).path("items");
    assertThat(items).hasSize(2);
    assertThat(items.get(0).path("practitionerId").asString()).isEqualTo(old.toString());
    assertThat(items.get(0).path("overdue").asBoolean()).isTrue();
    assertThat(items.get(0).path("displayName").asString()).isEqualTo("Dr Ancien");
    assertThat(items.get(0).path("city").asString()).isEqualTo("Lyon");
    assertThat(items.get(0).path("documentsCount").asInt()).isEqualTo(1);
    assertThat(items.get(0).path("missingHints")).hasSize(1);
    assertThat(items.get(0).path("missingHints").get(0).asString()).isEqualTo("identity");
    assertThat(items.get(0).path("submittedAt").asString()).isNotBlank();
    assertThat(items.get(1).path("practitionerId").asString()).isEqualTo(recent.toString());
    assertThat(items.get(1).path("overdue").asBoolean()).isFalse();
    assertThat(items.get(1).path("missingHints")).hasSize(2);
    assertThat(body(r).path("nextCursor").isNull()).isTrue();

    MvcResult newest = list(admin(), "?sort=newest");
    assertThat(body(newest).path("items").get(0).path("practitionerId").asString())
        .isEqualTo(recent.toString());
  }

  @Test
  void queueFiltersOnStatus() throws Exception {
    UUID review = queued("Dr En Revue", "under_review", Instant.now());
    UUID more = queued("Dr Complement", "needs_more", Instant.now());
    UUID adminId = admin();

    JsonNode needsMore = body(list(adminId, "?status=needs_more")).path("items");
    assertThat(needsMore).hasSize(1);
    assertThat(needsMore.get(0).path("practitionerId").asString()).isEqualTo(more.toString());

    JsonNode underReview = body(list(adminId, "")).path("items");
    assertThat(underReview).hasSize(1);
    assertThat(underReview.get(0).path("practitionerId").asString()).isEqualTo(review.toString());
  }

  @Test
  void queuePaginatesWithAnOpaqueCursor() throws Exception {
    Instant now = Instant.now();
    UUID second = queued("Dr Deuxieme", "under_review", now.minus(Duration.ofHours(2)));
    UUID first = queued("Dr Premier", "under_review", now.minus(Duration.ofHours(3)));
    UUID third = queued("Dr Troisieme", "under_review", now.minus(Duration.ofHours(1)));
    UUID adminId = admin();

    JsonNode page1 = body(list(adminId, "?limit=1"));
    assertThat(page1.path("items")).hasSize(1);
    assertThat(page1.path("items").get(0).path("practitionerId").asString())
        .isEqualTo(first.toString());
    String cursor = page1.path("nextCursor").asString();
    assertThat(cursor).isNotBlank();

    JsonNode page2 = body(list(adminId, "?limit=1&cursor=" + cursor));
    assertThat(page2.path("items").get(0).path("practitionerId").asString())
        .isEqualTo(second.toString());
    assertThat(page2.path("nextCursor").asString()).isNotBlank();

    JsonNode page3 = body(list(adminId, "?limit=1&cursor=" + page2.path("nextCursor").asString()));
    assertThat(page3.path("items").get(0).path("practitionerId").asString())
        .isEqualTo(third.toString());
    assertThat(page3.path("nextCursor").isNull()).isTrue();

    // Ordre inverse : le curseur de la page « newest » reprend après le plus récent.
    JsonNode newest1 = body(list(adminId, "?sort=newest&limit=1"));
    assertThat(newest1.path("items").get(0).path("practitionerId").asString())
        .isEqualTo(third.toString());
    JsonNode newest2 =
        body(list(adminId, "?sort=newest&limit=1&cursor=" + newest1.path("nextCursor").asString()));
    assertThat(newest2.path("items").get(0).path("practitionerId").asString())
        .isEqualTo(second.toString());

    MvcResult badCursor = list(adminId, "?cursor=n-importe-quoi");
    assertThat(status(badCursor)).isEqualTo(422);
    assertThat(body(badCursor).path("errors").get(0).path("field").asString()).isEqualTo("cursor");
  }

  @Test
  void queueFallsBackToUpdatedAtWhenThereIsNoSubmissionEvent() throws Exception {
    UUID noEvent = queued("Dr Sans Evenement", "under_review", null);
    JsonNode items = body(list(admin(), "")).path("items");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).path("practitionerId").asString()).isEqualTo(noEvent.toString());
    assertThat(items.get(0).path("submittedAt").isNull()).isTrue();
    assertThat(items.get(0).path("overdue").asBoolean()).isFalse();
  }

  @Test
  void unknownStatusIsRefusedWith422() throws Exception {
    MvcResult r = list(admin(), "?status=bidon");
    assertThat(status(r)).isEqualTo(422);
    assertThat(body(r).path("errors").get(0).path("field").asString()).isEqualTo("status");
    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
  }

  // ---- 3. dossier

  @Test
  void dossierShowsIdentityContactDocumentsAndDecisionsWithoutForbiddenFields() throws Exception {
    Dossier d = dossierWithDocuments("under_review", Instant.now().minus(Duration.ofHours(2)));
    UUID adminId = admin();
    Map<String, Object> payload =
        decision("needs_more", "Merci d'ajouter un justificatif lisible.");
    payload.put("internalNote", "Scan flou");
    payload.put("checklist", Map.of("nameMatches", true, "readable", false));
    assertThat(status(decide(adminId, d.id(), payload))).isEqualTo(201);

    MvcResult r = mvc.perform(get(BASE + "/" + d.id()).with(asAdmin(adminId))).andReturn();
    assertThat(status(r)).isEqualTo(200);
    JsonNode dossier = body(r);
    assertThat(dossier.path("practitionerId").asString()).isEqualTo(d.id().toString());
    assertThat(dossier.path("displayName").asString()).isEqualTo("Dr Claire Moreau");
    assertThat(dossier.path("firstName").asString()).isEqualTo("Maya");
    assertThat(dossier.path("email").asString()).isEqualTo(d.id() + "@example.test");
    assertThat(dossier.path("profession").asString()).isEqualTo("psychologist");
    assertThat(dossier.path("locale").asString()).isEqualTo("fr");
    assertThat(dossier.path("verificationStatus").asString()).isEqualTo("needs_more");
    assertThat(dossier.path("createdAt").asString()).isNotBlank();
    assertThat(dossier.path("emailConfirmedAt").asString()).isNotBlank();
    assertThat(dossier.path("submittedAt").asString()).isNotBlank();
    assertThat(dossier.path("overdue").asBoolean()).isFalse();
    assertThat(dossier.path("contact").path("phone").asString()).isEqualTo("06 12 34 56 78");
    assertThat(dossier.path("contact").path("professionalNumber").asString())
        .isEqualTo("ADELI123456");
    assertThat(dossier.path("contact").path("address").path("city").asString()).isEqualTo("Lyon");
    assertThat(dossier.path("contact").path("remoteOnly").asBoolean()).isFalse();

    JsonNode docs = dossier.path("documents");
    assertThat(docs).hasSize(2);
    assertThat(docs.get(0).path("type").asString()).isEqualTo("diploma");
    assertThat(docs.get(0).path("mime").asString()).isEqualTo("application/pdf");
    assertThat(docs.get(0).path("reviewedStatus").asString()).isEqualTo("pending");
    assertThat(docs.get(0).path("purged").asBoolean()).isFalse();

    JsonNode decisions = dossier.path("decisions");
    assertThat(decisions).hasSize(1);
    assertThat(decisions.get(0).path("kind").asString()).isEqualTo("needs_more");
    assertThat(decisions.get(0).path("internalNote").asString()).isEqualTo("Scan flou");
    assertThat(decisions.get(0).path("checklist").path("nameMatches").asBoolean()).isTrue();
    assertThat(decisions.get(0).path("adminId").asString()).isEqualTo(adminId.toString());

    assertNoForbiddenKey(dossier, "$");
  }

  private void assertNoForbiddenKey(JsonNode node, String path) {
    if (node.isObject()) {
      for (Map.Entry<String, JsonNode> e : node.properties()) {
        String key = e.getKey().toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN_KEY_PARTS) {
          assertThat(key).as("clé " + path + "." + e.getKey()).doesNotContain(forbidden);
        }
        assertNoForbiddenKey(e.getValue(), path + "." + e.getKey());
      }
    } else if (node.isArray()) {
      int i = 0;
      for (JsonNode child : node) {
        assertNoForbiddenKey(child, path + "[" + i++ + "]");
      }
    }
  }

  @Test
  void unknownDossierAndAdminAccountsHaveNoDossier() throws Exception {
    UUID adminId = admin();
    MvcResult unknown =
        mvc.perform(get(BASE + "/" + UUID.randomUUID()).with(asAdmin(adminId))).andReturn();
    assertThat(status(unknown)).isEqualTo(404);
    assertThat(body(unknown).path("code").asString()).isEqualTo("ADMIN_DOSSIER_NOT_FOUND");

    MvcResult adminTarget =
        mvc.perform(get(BASE + "/" + adminId).with(asAdmin(adminId))).andReturn();
    assertThat(status(adminTarget)).isEqualTo(404);
    assertThat(body(adminTarget).path("code").asString()).isEqualTo("ADMIN_DOSSIER_NOT_FOUND");
  }

  // ---- 4. URL signée

  @Test
  void signedUrlServesTheDecryptedFileAndLogsEachOpening() throws Exception {
    Dossier d = dossierWithDocuments("under_review", Instant.now());
    UUID adminId = admin();

    MvcResult urlResult =
        mvc.perform(
                get(BASE + "/" + d.id() + "/documents/" + d.diploma() + "/url")
                    .with(asAdmin(adminId)))
            .andReturn();
    assertThat(status(urlResult)).isEqualTo(200);
    String url = body(urlResult).path("url").asString();
    assertThat(url).startsWith(BASE + "/" + d.id() + "/documents/" + d.diploma() + "/file?exp=");
    Instant expiresAt = Instant.parse(body(urlResult).path("expiresAt").asString());
    assertThat(expiresAt)
        .isBetween(
            Instant.now().plus(Duration.ofMinutes(4)), Instant.now().plus(Duration.ofMinutes(6)));
    // La demande d'URL n'écrit rien.
    assertThat(count("select count(*) from document_access_log where document_id = ?", d.diploma()))
        .isZero();

    MvcResult file = mvc.perform(get(url).with(asAdmin(adminId))).andReturn();
    assertThat(status(file)).isEqualTo(200);
    assertThat(file.getResponse().getContentAsByteArray()).isEqualTo(PDF);
    assertThat(file.getResponse().getContentType()).isEqualTo("application/pdf");
    assertThat(file.getResponse().getHeader("Cache-Control")).contains("no-store");
    assertThat(file.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");

    assertThat(
            count(
                "select count(*) from document_access_log where document_id = ? and admin_id = ?",
                d.diploma(),
                adminId))
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from security_event where kind = 'admin.document_read'"
                    + " and actor_id = ? and practitioner_id = ?"
                    + " and details ->> 'documentId' = ?",
                adminId,
                d.id(),
                d.diploma().toString()))
        .isEqualTo(1);

    // Signature altérée, échéance dépassée avec la signature d'origine, autre administrateur.
    String sig = url.substring(url.indexOf("&sig=") + 5);
    String tampered = url.substring(0, url.length() - 1) + (sig.endsWith("A") ? "B" : "A");
    MvcResult bad = mvc.perform(get(tampered).with(asAdmin(adminId))).andReturn();
    assertThat(status(bad)).isEqualTo(403);
    assertThat(body(bad).path("code").asString()).isEqualTo("ADMIN_SIGNED_URL_INVALID");

    long past = Instant.now().minusSeconds(10).getEpochSecond();
    String expired =
        BASE + "/" + d.id() + "/documents/" + d.diploma() + "/file?exp=" + past + "&sig=" + sig;
    assertThat(status(mvc.perform(get(expired).with(asAdmin(adminId))).andReturn())).isEqualTo(403);

    assertThat(status(mvc.perform(get(url).with(asAdmin(admin()))).andReturn())).isEqualTo(403);
    assertThat(
            status(
                mvc.perform(
                        get(BASE + "/" + d.id() + "/documents/" + d.diploma() + "/file")
                            .with(asAdmin(adminId)))
                    .andReturn()))
        .isEqualTo(403);

    // Les refus n'ont rien consigné de plus.
    assertThat(count("select count(*) from document_access_log where document_id = ?", d.diploma()))
        .isEqualTo(1);
  }

  @Test
  void signedUrlRefusesUnknownRemovedAndForeignDocuments() throws Exception {
    Dossier d = dossierWithDocuments("under_review", Instant.now());
    Dossier other = dossierWithDocuments("under_review", Instant.now());
    UUID adminId = admin();

    MvcResult unknown =
        mvc.perform(
                get(BASE + "/" + d.id() + "/documents/" + UUID.randomUUID() + "/url")
                    .with(asAdmin(adminId)))
            .andReturn();
    assertThat(status(unknown)).isEqualTo(404);
    assertThat(body(unknown).path("code").asString()).isEqualTo("ADMIN_DOCUMENT_NOT_FOUND");

    MvcResult foreign =
        mvc.perform(
                get(BASE + "/" + d.id() + "/documents/" + other.diploma() + "/url")
                    .with(asAdmin(adminId)))
            .andReturn();
    assertThat(status(foreign)).isEqualTo(404);

    jdbc.update("update verification_document set deleted_at = now() where id = ?", d.identity());
    MvcResult removed =
        mvc.perform(
                get(BASE + "/" + d.id() + "/documents/" + d.identity() + "/url")
                    .with(asAdmin(adminId)))
            .andReturn();
    assertThat(status(removed)).isEqualTo(404);

    jdbc.update("update verification_document set purged_at = now() where id = ?", d.diploma());
    MvcResult purged =
        mvc.perform(
                get(BASE + "/" + d.id() + "/documents/" + d.diploma() + "/url")
                    .with(asAdmin(adminId)))
            .andReturn();
    assertThat(status(purged)).isEqualTo(410);
    assertThat(body(purged).path("code").asString()).isEqualTo("ADMIN_DOCUMENT_PURGED");
  }

  // ---- 5. qualification des justificatifs

  @Test
  void reviewingADocumentRecordsStatusAndReviewer() throws Exception {
    Dossier d = dossierWithDocuments("under_review", Instant.now());
    UUID adminId = admin();

    MvcResult ok = review(adminId, d, d.diploma(), "conform");
    assertThat(status(ok)).isEqualTo(200);
    assertThat(body(ok).path("reviewedStatus").asString()).isEqualTo("conform");
    assertThat(body(ok).path("reviewedAt").asString()).isNotBlank();
    assertThat(
            jdbc.queryForObject(
                "select reviewed_by from verification_document where id = ?",
                UUID.class,
                d.diploma()))
        .isEqualTo(adminId);
    assertThat(
            jdbc.queryForObject(
                "select reviewed_status from verification_document where id = ?",
                String.class,
                d.diploma()))
        .isEqualTo("conform");

    MvcResult bad = review(adminId, d, d.diploma(), "bidon");
    assertThat(status(bad)).isEqualTo(422);
    assertThat(body(bad).path("errors").get(0).path("field").asString())
        .isEqualTo("reviewedStatus");

    MvcResult missing =
        mvc.perform(
                patch(BASE + "/" + d.id() + "/documents/" + d.diploma())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .with(asAdmin(adminId)))
            .andReturn();
    assertThat(status(missing)).isEqualTo(422);

    MvcResult unknown = review(adminId, d, UUID.randomUUID(), "conform");
    assertThat(status(unknown)).isEqualTo(404);
  }

  // ---- 6. complément demandé

  @Test
  void needsMoreWithoutMessageIsRefusedAndChangesNothing() throws Exception {
    Dossier d = dossierWithDocuments("under_review", Instant.now());
    MvcResult r = decide(admin(), d.id(), decision("needs_more", "  "));
    assertThat(status(r)).isEqualTo(422);
    assertThat(body(r).path("errors").get(0).path("field").asString())
        .isEqualTo("messageToPractitioner");
    assertThat(body(r).path("errors").get(0).path("code").asString()).isEqualTo("MESSAGE_REQUIRED");
    assertThat(
            count("select count(*) from verification_decision where practitioner_id = ?", d.id()))
        .isZero();
    assertThat(statusOf(d.id())).isEqualTo("under_review");
    assertThat(
            count(
                "select count(*) from security_event where practitioner_id = ?"
                    + " and kind = 'admin.decision'",
                d.id()))
        .isZero();
  }

  @Test
  void invalidDecisionInputsAreRefusedWith422() throws Exception {
    Dossier d = dossierWithDocuments("under_review", Instant.now());
    UUID adminId = admin();

    MvcResult noKind = decide(adminId, d.id(), new LinkedHashMap<>());
    assertThat(status(noKind)).isEqualTo(422);
    assertThat(body(noKind).path("errors").get(0).path("field").asString()).isEqualTo("kind");

    MvcResult badKind = decide(adminId, d.id(), decision("suspended", "x"));
    assertThat(status(badKind)).isEqualTo(422);
    assertThat(body(badKind).path("errors").get(0).path("field").asString()).isEqualTo("kind");

    MvcResult tooLong = decide(adminId, d.id(), decision("needs_more", "x".repeat(4001)));
    assertThat(status(tooLong)).isEqualTo(422);
    assertThat(body(tooLong).path("errors").get(0).path("field").asString())
        .isEqualTo("messageToPractitioner");

    Map<String, Object> longNote = decision("needs_more", "ok");
    longNote.put("internalNote", "y".repeat(4001));
    MvcResult noteTooLong = decide(adminId, d.id(), longNote);
    assertThat(status(noteTooLong)).isEqualTo(422);
    assertThat(body(noteTooLong).path("errors").get(0).path("field").asString())
        .isEqualTo("internalNote");

    assertThat(statusOf(d.id())).isEqualTo("under_review");
  }

  @Test
  void needsMoreWithMessageUpdatesStatusSendsEmailAndSchedulesPurge() throws Exception {
    Dossier d = dossierWithDocuments("under_review", Instant.now());
    UUID adminId = admin();
    Instant before = Instant.now();

    Map<String, Object> payload = decision("needs_more", "Merci de fournir un diplôme lisible.");
    payload.put("internalNote", "Scan flou");
    payload.put("checklist", Map.of("readable", false));
    MvcResult r = decide(adminId, d.id(), payload);
    assertThat(status(r)).isEqualTo(201);
    JsonNode created = body(r);
    assertThat(created.path("kind").asString()).isEqualTo("needs_more");
    assertThat(created.path("verificationStatus").asString()).isEqualTo("needs_more");
    assertThat(created.path("adminId").asString()).isEqualTo(adminId.toString());
    assertThat(created.path("checklist").path("readable").asBoolean()).isFalse();
    assertThat(created.path("id").asString()).isNotBlank();

    assertThat(statusOf(d.id())).isEqualTo("needs_more");
    assertThat(
            count("select count(*) from verification_decision where practitioner_id = ?", d.id()))
        .isEqualTo(1);

    String mail = mails.lastText(d.id() + "@example.test", "dossier");
    assertThat(mail).isNotNull().contains("Merci de fournir un diplôme lisible.");

    assertThat(
            count(
                "select count(*) from security_event where practitioner_id = ? and actor_id = ?"
                    + " and kind = 'admin.decision' and details ->> 'kind' = 'needs_more'"
                    + " and details ->> 'decisionId' = ?",
                d.id(),
                adminId,
                created.path("id").asString()))
        .isEqualTo(1);

    List<Timestamp> purges =
        jdbc.queryForList(
            "select purge_after from verification_document where practitioner_id = ?",
            Timestamp.class,
            d.id());
    assertThat(purges).hasSize(2);
    for (Timestamp purge : purges) {
      assertThat(purge.toInstant())
          .isBetween(
              before.plus(Duration.ofDays(90)).minusSeconds(60),
              Instant.now().plus(Duration.ofDays(90)).plusSeconds(60));
    }
  }

  @Test
  void decisionsAreAppendOnlyAndAFollowUpDecisionIsPossibleOnNeedsMore() throws Exception {
    Dossier d = dossierWithDocuments("under_review", Instant.now());
    UUID adminId = admin();
    assertThat(status(decide(adminId, d.id(), decision("needs_more", "Premier message"))))
        .isEqualTo(201);
    assertThat(status(decide(adminId, d.id(), decision("needs_more", "Second message"))))
        .isEqualTo(201);
    assertThat(
            count("select count(*) from verification_decision where practitioner_id = ?", d.id()))
        .isEqualTo(2);
  }

  // ---- 7. approbation

  @Test
  void approvalNeedsConformDocumentsUnlessForcedWithAnInternalNote() throws Exception {
    Dossier d = dossierWithDocuments("under_review", Instant.now());
    UUID adminId = admin();

    MvcResult incomplete = decide(adminId, d.id(), decision("approved", null));
    assertThat(status(incomplete)).isEqualTo(409);
    assertThat(body(incomplete).path("code").asString()).isEqualTo("ADMIN_APPROVAL_INCOMPLETE");
    JsonNode pending = body(incomplete).path("pendingDocuments");
    assertThat(pending).hasSize(2);
    assertThat(statusOf(d.id())).isEqualTo("under_review");

    assertThat(status(review(adminId, d, d.diploma(), "conform"))).isEqualTo(200);
    MvcResult stillIncomplete = decide(adminId, d.id(), decision("approved", null));
    assertThat(status(stillIncomplete)).isEqualTo(409);
    assertThat(body(stillIncomplete).path("pendingDocuments")).hasSize(1);
    assertThat(body(stillIncomplete).path("pendingDocuments").get(0).asString())
        .isEqualTo(d.identity().toString());

    Map<String, Object> forcedNoNote = decision("approved", null);
    forcedNoNote.put("force", true);
    MvcResult noNote = decide(adminId, d.id(), forcedNoNote);
    assertThat(status(noNote)).isEqualTo(422);
    assertThat(body(noNote).path("errors").get(0).path("field").asString())
        .isEqualTo("internalNote");
    assertThat(statusOf(d.id())).isEqualTo("under_review");

    Map<String, Object> forced = decision("approved", null);
    forced.put("force", true);
    forced.put("internalNote", "Pièce d'identité vérifiée en visio.");
    assertThat(status(decide(adminId, d.id(), forced))).isEqualTo(201);
    assertThat(statusOf(d.id())).isEqualTo("approved");
  }

  @Test
  void approvalWithAllDocumentsConformApprovesPublishesEventAndEmails() throws Exception {
    Dossier d = dossierWithDocuments("under_review", Instant.now());
    UUID adminId = admin();
    assertThat(status(review(adminId, d, d.diploma(), "conform"))).isEqualTo(200);
    assertThat(status(review(adminId, d, d.identity(), "conform"))).isEqualTo(200);

    MvcResult r = decide(adminId, d.id(), decision("approved", null));
    assertThat(status(r)).isEqualTo(201);
    assertThat(body(r).path("verificationStatus").asString()).isEqualTo("approved");

    assertThat(facade.find(d.id()).orElseThrow().verificationStatus()).isEqualTo("approved");
    assertThat(
            jdbc.queryForObject(
                "select verified_by from practitioner where id = ?", UUID.class, d.id()))
        .isEqualTo(adminId);
    assertThat(
            jdbc.queryForObject(
                "select verified_at is not null from practitioner where id = ?",
                Boolean.class,
                d.id()))
        .isTrue();
    assertThat(await(() -> recorder.approved.contains(d.id()))).isTrue();
    assertThat(mails.lastText(d.id() + "@example.test", "dossier")).isNotNull();
    assertThat(count("select count(*) from document_access_log where admin_id = ?", adminId))
        .isZero();
  }

  // ---- 8. refus

  @Test
  void rejectionSuspendsTheAccountAndRevokesEverySession() throws Exception {
    String email = "r" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
    Map<String, Object> registration = new LinkedHashMap<>();
    registration.put("email", email);
    registration.put("password", PASSWORD);
    registration.put("firstName", "Maya");
    registration.put("lastName", "Kaddour");
    registration.put("profession", "psychologist");
    registration.put("phone", "06 12 34 56 78");
    registration.put(
        "address", Map.of("street", "12 rue des Lilas", "postalCode", "69003", "city", "Lyon"));
    registration.put("remoteOnly", false);
    registration.put("consents", Map.of("terms", true, "privacy", true));
    assertThat(
            status(
                mvc.perform(
                        post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(registration)))
                    .andReturn()))
        .isEqualTo(201);
    UUID id =
        jdbc.queryForObject(
            "select id from practitioner where email_normalized = ?", UUID.class, email);
    jdbc.update("update practitioner set verification_status = 'under_review' where id = ?", id);
    submitted(id, Instant.now());

    Map<String, Object> credentials = Map.of("email", email, "password", PASSWORD);
    MvcResult login = loginFrom(credentials);
    assertThat(status(login)).isEqualTo(200);
    Cookie cookie = login.getResponse().getCookie("SESSION");
    assertThat(cookie).isNotNull();
    assertThat(status(mvc.perform(get("/api/auth/me").cookie(cookie)).andReturn())).isEqualTo(200);
    assertThat(count("select count(*) from spring_session where principal_name = ?", id.toString()))
        .isEqualTo(1);

    UUID adminId = admin();
    Instant before = Instant.now();
    MvcResult r =
        decide(adminId, id, decision("rejected", "Le diplôme ne correspond pas à la profession."));
    assertThat(status(r)).isEqualTo(201);
    assertThat(body(r).path("verificationStatus").asString()).isEqualTo("rejected");

    assertThat(status(mvc.perform(get("/api/auth/me").cookie(cookie)).andReturn())).isEqualTo(401);
    assertThat(count("select count(*) from spring_session where principal_name = ?", id.toString()))
        .isZero();
    MvcResult relogin = loginFrom(credentials);
    assertThat(status(relogin)).isEqualTo(403);
    assertThat(body(relogin).path("code").asString()).isEqualTo("ACCOUNT_SUSPENDED");

    assertThat(statusOf(id)).isEqualTo("rejected");
    Timestamp suspendedAt =
        jdbc.queryForObject(
            "select suspended_at from practitioner where id = ?", Timestamp.class, id);
    Timestamp purgeAfter =
        jdbc.queryForObject(
            "select purge_after from practitioner where id = ?", Timestamp.class, id);
    assertThat(suspendedAt).isNotNull();
    assertThat(purgeAfter.toInstant())
        .isBetween(
            before.plus(Duration.ofDays(30)).minusSeconds(60),
            Instant.now().plus(Duration.ofDays(30)).plusSeconds(60));

    String mail = mails.lastText(email, "dossier");
    assertThat(mail)
        .isNotNull()
        .contains("Le diplôme ne correspond pas à la profession.")
        .contains("contester cette décision");
    assertThat(
            count(
                "select count(*) from security_event where practitioner_id = ? and actor_id = ?"
                    + " and kind = 'session_revoked' and details ->> 'reason' = 'rejected'"
                    + " and (details ->> 'count')::int = 1",
                id,
                adminId))
        .isEqualTo(1);
    assertThat(await(() -> recorder.rejected.contains(id))).isTrue();
  }

  private MvcResult loginFrom(Map<String, Object> credentials) throws Exception {
    return mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(credentials))
                .with(
                    request -> {
                      request.setRemoteAddr("203.0.113.10");
                      return request;
                    }))
        .andReturn();
  }

  // ---- 9. dossier non décidable

  @Test
  void decisionOnAnAlreadyApprovedDossierIsRefused() throws Exception {
    UUID approved = practitioner("Dr Valide", "approved");
    MvcResult r = decide(admin(), approved, decision("needs_more", "Trop tard"));
    assertThat(status(r)).isEqualTo(409);
    assertThat(body(r).path("code").asString()).isEqualTo("ADMIN_DOSSIER_NOT_REVIEWABLE");
    assertThat(
            count("select count(*) from verification_decision where practitioner_id = ?", approved))
        .isZero();
  }

  @Test
  void decisionOnAnAdminAccountOrAnUnknownIdIsNotFound() throws Exception {
    UUID adminId = admin();
    assertThat(status(decide(adminId, adminId, decision("needs_more", "x")))).isEqualTo(404);
    assertThat(status(decide(adminId, UUID.randomUUID(), decision("needs_more", "x"))))
        .isEqualTo(404);
  }

  // ---- 10. rappel quotidien

  @Test
  void overdueReminderEmailsActiveAdminsAndCountsOverdueDossiers() throws Exception {
    UUID adminId = admin();
    String adminEmail = adminId + "@example.test";
    queued("Dr En Retard", "under_review", Instant.now().minus(Duration.ofDays(5)));
    queued("Dr Dans Les Temps", "under_review", Instant.now().minus(Duration.ofHours(1)));

    assertThat(service.sendOverdueReminders()).isEqualTo(1);

    String mail = mails.lastText(adminEmail, "retard");
    assertThat(mail).isNotNull().contains("Dr En Retard — psychologue — soumis le");
    assertThat(mail).doesNotContain("Dr Dans Les Temps");
    assertThat(mails.count(adminEmail, "retard")).isEqualTo(1);
  }

  @Test
  void overdueReminderDoesNothingWhenNoDossierIsOverdue() throws Exception {
    UUID adminId = admin();
    queued("Dr Dans Les Temps", "under_review", Instant.now().minus(Duration.ofHours(1)));

    assertThat(service.sendOverdueReminders()).isZero();
    assertThat(mails.count(adminId + "@example.test", "retard")).isZero();
  }

  @Test
  void adminCannotBeListedAsADossierInTheQueue() throws Exception {
    UUID adminId = admin();
    jdbc.update(
        "update practitioner set verification_status = 'under_review' where id = ?", adminId);
    try {
      assertThat(body(list(adminId, "")).path("items")).isEmpty();
    } finally {
      jdbc.update("update practitioner set verification_status = 'approved' where id = ?", adminId);
    }
  }
}
