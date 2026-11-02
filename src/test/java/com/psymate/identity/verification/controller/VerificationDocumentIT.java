package com.psymate.identity.verification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.verification.VerificationTestConfig;
import com.psymate.identity.verification.service.DocumentSanitizerTest;
import com.psymate.identity.verification.service.StorageService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** M1-13 : dossier professionnel (fiche 13), de bout en bout sur Postgres et Mailpit. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VerificationTestConfig.class)
class VerificationDocumentIT {

  private static final byte[] PDF =
      ("%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n"
              + "contenu confidentiel diplome ".repeat(20))
          .getBytes(StandardCharsets.US_ASCII);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private StorageService storage;
  @Autowired private VerificationTestConfig.Recorder recorder;
  @Autowired private GenericContainer<?> mailpit;
  private final JsonMapper json = JsonMapper.builder().build();

  private UUID tenant;

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  // ---- fixtures

  private UUID practitioner(String profession, String status, String role) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status, role)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test Praticien', ?, ?, ?)",
        id,
        tenant,
        email,
        email,
        profession,
        status,
        role);
    return id;
  }

  private UUID practitioner(String profession, String status) {
    return practitioner(profession, status, "practitioner");
  }

  private RequestPostProcessor as(UUID id, String profession, String status) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            id, tenant, id + "@example.test", "Dr Test Praticien", profession, status);
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions upload(RequestPostProcessor who, String type, String filename, byte[] bytes)
      throws Exception {
    return mvc.perform(
        multipart("/api/me/verification/documents")
            .file(new MockMultipartFile("file", filename, "application/pdf", bytes))
            .param("type", type)
            .param("title", "Master de psychologie")
            .param("issuer", "Université de Lyon")
            .param("year", "2012")
            .with(who));
  }

  private UUID uploadOk(RequestPostProcessor who, String type) throws Exception {
    MvcResult r = upload(who, type, type + ".pdf", PDF).andExpect(status().isCreated()).andReturn();
    return UUID.fromString(
        json.readTree(r.getResponse().getContentAsString()).path("id").asString());
  }

  // ---- lecture initiale

  @Test
  void getReturnsEmptyDossierWithMissingAndRecommended() throws Exception {
    UUID id = practitioner("psychologist", "docs_pending");
    mvc.perform(get("/api/me/verification").with(as(id, "psychologist", "docs_pending")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("docs_pending"))
        .andExpect(jsonPath("$.documents").isEmpty())
        .andExpect(jsonPath("$.missing[0]").value("diploma"))
        .andExpect(jsonPath("$.missing[1]").value("identity"))
        .andExpect(jsonPath("$.recommended[0]").value("registration"))
        .andExpect(jsonPath("$.decisions").isEmpty())
        .andExpect(jsonPath("$.submittedAt").doesNotExist());
  }

  // ---- dépôt, chiffrement, lecture par URL signée

  @Test
  void fileOnDiskIsEncryptedAndComesBackIdenticalThroughSignedUrl() throws Exception {
    UUID id = practitioner("psychologist", "docs_pending");
    RequestPostProcessor me = as(id, "psychologist", "docs_pending");
    MvcResult created =
        upload(me, "diploma", "diplome.pdf", PDF)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("diploma"))
            .andExpect(jsonPath("$.mime").value("application/pdf"))
            .andExpect(jsonPath("$.sizeBytes").value(PDF.length))
            .andExpect(jsonPath("$.reviewedStatus").value("pending"))
            .andExpect(jsonPath("$.storageKey").doesNotExist())
            .andReturn();
    UUID docId =
        UUID.fromString(
            json.readTree(created.getResponse().getContentAsString()).path("id").asString());

    Map<String, Object> row =
        jdbc.queryForMap(
            "select storage_key, sha256, purpose, size_bytes from verification_document where id = ?",
            docId);
    assertThat(row.get("purpose")).isEqualTo("verification");
    assertThat(row.get("sha256")).asString().hasSize(64);
    byte[] onDisk = storage.get((String) row.get("storage_key"));
    assertThat(onDisk).isNotEqualTo(PDF);
    assertThat(new String(onDisk, StandardCharsets.ISO_8859_1))
        .doesNotContain("%PDF")
        .doesNotContain("confidentiel");

    MvcResult urlResult =
        mvc.perform(get("/api/me/verification/documents/" + docId + "/url").with(me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").exists())
            .andReturn();
    String url = json.readTree(urlResult.getResponse().getContentAsString()).path("url").asString();
    MvcResult served =
        mvc.perform(get(url).with(me))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andReturn();
    assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(PDF);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from document_access_log where document_id = ? and admin_id = ?",
                Integer.class,
                docId,
                id))
        .isEqualTo(1);

    mvc.perform(get("/api/me/verification").with(me))
        .andExpect(jsonPath("$.documents.length()").value(1))
        .andExpect(jsonPath("$.missing[0]").value("identity"));
  }

  @Test
  void signedUrlWithTamperedSignatureOrOtherDocumentIsRefused() throws Exception {
    UUID id = practitioner("coach", "docs_pending");
    RequestPostProcessor me = as(id, "coach", "docs_pending");
    UUID a = uploadOk(me, "diploma");
    UUID b = uploadOk(me, "identity");
    String url =
        json.readTree(
                mvc.perform(get("/api/me/verification/documents/" + a + "/url").with(me))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("url")
            .asString();
    mvc.perform(get(url.replace("/documents/" + a, "/documents/" + b)).with(me))
        .andExpect(status().isForbidden());
    mvc.perform(get(url.substring(0, url.length() - 2) + "xx").with(me))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/me/verification/documents/" + a + "/file").with(me))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from document_access_log where document_id in (?, ?)",
                Integer.class,
                a,
                b))
        .isZero();
  }

  @Test
  void signedUrlExpiredAfterFiveMinutesIsRefused() throws Exception {
    UUID id = practitioner("coach", "docs_pending");
    RequestPostProcessor me = as(id, "coach", "docs_pending");
    UUID doc = uploadOk(me, "diploma");
    long expired = java.time.Instant.now().minus(Duration.ofMinutes(1)).getEpochSecond();
    // Signature valide pour une échéance passée : refusée sur l'échéance, pas sur la forme.
    String url =
        json.readTree(
                mvc.perform(get("/api/me/verification/documents/" + doc + "/url").with(me))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("url")
            .asString();
    mvc.perform(get(url.replaceAll("exp=\\d+", "exp=" + expired)).with(me))
        .andExpect(status().isForbidden());
    // L'échéance annoncée est bien à 5 minutes.
    JsonNode body =
        json.readTree(
            mvc.perform(get("/api/me/verification/documents/" + doc + "/url").with(me))
                .andReturn()
                .getResponse()
                .getContentAsString());
    long seconds =
        java.time.Instant.parse(body.path("expiresAt").asString()).getEpochSecond()
            - java.time.Instant.now().getEpochSecond();
    assertThat(seconds).isBetween(280L, 300L);
  }

  // ---- contrôles de fichier

  @Test
  void exeRenamedPdfIs415ProblemJson() throws Exception {
    UUID id = practitioner("psychologist", "docs_pending");
    byte[] exe = new byte[2000];
    exe[0] = 'M';
    exe[1] = 'Z';
    upload(as(id, "psychologist", "docs_pending"), "diploma", "diplome.pdf", exe)
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("DOCUMENT_UNSUPPORTED"))
        .andExpect(jsonPath("$.status").value(415));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from verification_document where practitioner_id = ?",
                Integer.class,
                id))
        .isZero();
  }

  @Test
  void elevenMegabytesIs413ProblemJson() throws Exception {
    UUID id = practitioner("psychologist", "docs_pending");
    byte[] big = new byte[11 * 1024 * 1024];
    System.arraycopy("%PDF-1.4".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 8);
    upload(as(id, "psychologist", "docs_pending"), "diploma", "gros.pdf", big)
        .andExpect(status().isContentTooLarge())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("DOCUMENT_TOO_LARGE"));
  }

  @Test
  void jpegExifIsStrippedBeforeStorage() throws Exception {
    UUID id = practitioner("coach", "docs_pending");
    RequestPostProcessor me = as(id, "coach", "docs_pending");
    byte[] jpeg = DocumentSanitizerTest.jpegWithExif();
    MvcResult r =
        mvc.perform(
                multipart("/api/me/verification/documents")
                    .file(new MockMultipartFile("file", "carte.jpg", "image/jpeg", jpeg))
                    .param("type", "identity")
                    .param("title", "Carte d'identité")
                    .with(me))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mime").value("image/jpeg"))
            .andReturn();
    UUID docId =
        UUID.fromString(json.readTree(r.getResponse().getContentAsString()).path("id").asString());
    String url =
        json.readTree(
                mvc.perform(get("/api/me/verification/documents/" + docId + "/url").with(me))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("url")
            .asString();
    byte[] back = mvc.perform(get(url).with(me)).andReturn().getResponse().getContentAsByteArray();
    assertThat(new String(back, StandardCharsets.ISO_8859_1))
        .doesNotContain("Exif")
        .doesNotContain("GPS-LATITUDE");
    assertThat(back).isNotEqualTo(jpeg);
  }

  @Test
  void invalidMetadataIs422WithFieldErrors() throws Exception {
    UUID id = practitioner("coach", "docs_pending");
    mvc.perform(
            multipart("/api/me/verification/documents")
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
                .param("type", "passport")
                .param("title", " ")
                .param("year", "1800")
                .with(as(id, "coach", "docs_pending")))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(jsonPath("$.errors[0].field").value("type"));
  }

  // ---- isolation entre praticiens

  @Test
  void aPractitionerNeverReadsAnotherOnesDocument() throws Exception {
    UUID owner = practitioner("psychologist", "docs_pending");
    UUID intruder = practitioner("psychologist", "docs_pending");
    UUID doc = uploadOk(as(owner, "psychologist", "docs_pending"), "diploma");
    RequestPostProcessor other = as(intruder, "psychologist", "docs_pending");

    mvc.perform(get("/api/me/verification/documents/" + doc + "/url").with(other))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mvc.perform(get("/api/me/verification/documents/" + doc).with(other))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/me/verification/documents/" + doc + "/file").with(other))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/me/verification/documents/" + doc).with(other))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/me/verification").with(other))
        .andExpect(jsonPath("$.documents").isEmpty());

    // Même avec une URL signée valide du propriétaire, l'intrus est refusé.
    String url =
        json.readTree(
                mvc.perform(
                        get("/api/me/verification/documents/" + doc + "/url")
                            .with(as(owner, "psychologist", "docs_pending")))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("url")
            .asString();
    mvc.perform(get(url).with(other)).andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from document_access_log where document_id = ?",
                Integer.class,
                doc))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select deleted_at is null from verification_document where id = ?",
                Boolean.class,
                doc))
        .isTrue();
  }

  @Test
  void unknownDocumentIs404() throws Exception {
    UUID id = practitioner("coach", "docs_pending");
    mvc.perform(
            get("/api/me/verification/documents/" + UUID.randomUUID() + "/url")
                .with(as(id, "coach", "docs_pending")))
        .andExpect(status().isNotFound());
  }

  // ---- retrait

  @Test
  void deleteIsSoftAndRemovesTheFile() throws Exception {
    UUID id = practitioner("coach", "docs_pending");
    RequestPostProcessor me = as(id, "coach", "docs_pending");
    UUID doc = uploadOk(me, "diploma");
    String key =
        jdbc.queryForObject(
            "select storage_key from verification_document where id = ?", String.class, doc);
    assertThat(storage.exists(key)).isTrue();

    mvc.perform(delete("/api/me/verification/documents/" + doc).with(me))
        .andExpect(status().isNoContent());

    assertThat(storage.exists(key)).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select deleted_at is not null from verification_document where id = ?",
                Boolean.class,
                doc))
        .isTrue();
    mvc.perform(get("/api/me/verification").with(me))
        .andExpect(jsonPath("$.documents").isEmpty())
        .andExpect(jsonPath("$.missing.length()").value(2));
    mvc.perform(get("/api/me/verification/documents/" + doc + "/url").with(me))
        .andExpect(status().isNotFound());
  }

  // ---- soumission

  @ParameterizedTest
  @ValueSource(strings = {"psychologist", "psychiatrist", "coach"})
  void submitWithoutIdentityIs409WithMissingForEveryProfession(String profession) throws Exception {
    UUID id = practitioner(profession, "docs_pending");
    RequestPostProcessor me = as(id, profession, "docs_pending");
    uploadOk(me, "diploma");
    mvc.perform(post("/api/me/verification/submit").with(me))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VERIFICATION_INCOMPLETE"))
        .andExpect(jsonPath("$.missing.length()").value(1))
        .andExpect(jsonPath("$.missing[0]").value("identity"));
    assertThat(statusOf(id)).isEqualTo("docs_pending");
  }

  @ParameterizedTest
  @ValueSource(strings = {"psychologist", "psychiatrist", "coach"})
  void submitWithoutDiplomaIs409(String profession) throws Exception {
    UUID id = practitioner(profession, "docs_pending");
    RequestPostProcessor me = as(id, profession, "docs_pending");
    uploadOk(me, "identity");
    mvc.perform(post("/api/me/verification/submit").with(me))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.missing[0]").value("diploma"))
        .andExpect(jsonPath("$.missing.length()").value(1));
  }

  @Test
  void submitWithNothingListsBothTypes() throws Exception {
    UUID id = practitioner("coach", "docs_pending");
    mvc.perform(post("/api/me/verification/submit").with(as(id, "coach", "docs_pending")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.missing[0]").value("diploma"))
        .andExpect(jsonPath("$.missing[1]").value("identity"));
  }

  @Test
  void completeSubmitMovesToUnderReviewPublishesEventAndMailsAdmins() throws Exception {
    String adminEmail = "admin-" + UUID.randomUUID() + "@example.test";
    UUID admin = practitioner("psychologist", "approved", "admin");
    jdbc.update(
        "update practitioner set email = ?, email_normalized = ? where id = ?",
        adminEmail,
        adminEmail,
        admin);

    UUID id = practitioner("psychologist", "docs_pending");
    RequestPostProcessor me = as(id, "psychologist", "docs_pending");
    uploadOk(me, "diploma");
    uploadOk(me, "identity");

    // Psychologue sans ADELI : soumission possible, ADELI seulement conseillée.
    mvc.perform(get("/api/me/verification").with(me))
        .andExpect(jsonPath("$.missing").isEmpty())
        .andExpect(jsonPath("$.recommended[0]").value("registration"));

    mvc.perform(post("/api/me/verification/submit").with(me))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("under_review"))
        .andExpect(jsonPath("$.submittedAt").exists());

    assertThat(statusOf(id)).isEqualTo("under_review");
    assertThat(recorder.submitted).contains(id);
    String mail = lastMail(adminEmail, "dossier");
    assertThat(mail).isNotNull().contains("Dr Test Praticien");

    // Verrou après soumission : ni retrait, ni nouveau dépôt, ni seconde soumission.
    UUID any =
        jdbc.queryForObject(
            "select id from verification_document where practitioner_id = ? limit 1",
            UUID.class,
            id);
    mvc.perform(delete("/api/me/verification/documents/" + any).with(me))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"));
    upload(me, "other", "autre.pdf", PDF).andExpect(status().isConflict());
    mvc.perform(post("/api/me/verification/submit").with(me)).andExpect(status().isConflict());
    mvc.perform(get("/api/me/verification").with(me)).andExpect(status().isOk());
  }

  @Test
  void needsMoreAllowsCompletingAndResubmitting() throws Exception {
    UUID id = practitioner("coach", "needs_more");
    RequestPostProcessor me = as(id, "coach", "needs_more");
    UUID adminId = practitioner("coach", "approved", "admin");
    jdbc.update(
        "insert into verification_decision (id, practitioner_id, admin_id, kind, internal_note,"
            + " message_to_practitioner) values (?, ?, ?, 'needs_more', 'NOTE-INTERNE-SECRETE',"
            + " 'Merci de joindre votre diplôme lisible.')",
        UUID.randomUUID(),
        id,
        adminId);
    jdbc.update(
        "insert into verification_decision (id, practitioner_id, admin_id, kind, internal_note)"
            + " values (?, ?, ?, 'approved', 'AUTRE-NOTE-INTERNE')",
        UUID.randomUUID(),
        id,
        adminId);

    MvcResult r =
        mvc.perform(get("/api/me/verification").with(me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decisions.length()").value(1))
            .andExpect(jsonPath("$.decisions[0].kind").value("needs_more"))
            .andExpect(
                jsonPath("$.decisions[0].message").value("Merci de joindre votre diplôme lisible."))
            .andReturn();
    assertThat(r.getResponse().getContentAsString())
        .doesNotContain("NOTE-INTERNE")
        .doesNotContain("internalNote")
        .doesNotContain("internal_note");

    uploadOk(me, "diploma");
    uploadOk(me, "identity");
    mvc.perform(post("/api/me/verification/submit").with(me))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("under_review"));
  }

  @Test
  void uploadIsRefusedOutsideEditableStates() throws Exception {
    UUID id = practitioner("coach", "approved");
    upload(as(id, "coach", "approved"), "diploma", "a.pdf", PDF)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"));
  }

  // ---- informations professionnelles

  @Test
  void patchProfessionalInfoMergesFields() throws Exception {
    UUID id = practitioner("psychologist", "docs_pending");
    RequestPostProcessor me = as(id, "psychologist", "docs_pending");
    mvc.perform(
            patch("/api/me/professional-info")
                .with(me)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"professionalNumber\":\"759912345\",\"phone\":\"06 12 34 56 78\","
                        + "\"address\":{\"street\":\"12 rue des Lilas\",\"postalCode\":\"69003\","
                        + "\"city\":\"Lyon\"},\"remoteOnly\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.professionalNumber").value("759912345"))
        .andExpect(jsonPath("$.address.city").value("Lyon"))
        .andExpect(jsonPath("$.remoteOnly").value(true));
    // Champ absent : inchangé ; chaîne vide : effacé.
    mvc.perform(
            patch("/api/me/professional-info")
                .with(me)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.professionalNumber").value("759912345"));
    assertThat(
            jdbc.queryForObject(
                "select professional_number from practitioner where id = ?", String.class, id))
        .isEqualTo("759912345");
    mvc.perform(get("/api/me/verification").with(me))
        .andExpect(jsonPath("$.professionalInfo.professionalNumber").value("759912345"));
    mvc.perform(
            patch("/api/me/professional-info")
                .with(me)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"abc\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("phone"));
  }

  @Test
  void unauthenticatedIs401() throws Exception {
    mvc.perform(get("/api/me/verification")).andExpect(status().isUnauthorized());
  }

  // ---- utilitaires

  private String statusOf(UUID id) {
    return jdbc.queryForObject(
        "select verification_status from practitioner where id = ?", String.class, id);
  }

  /** Texte du dernier e-mail reçu par Mailpit pour ce destinataire ; null après 10 s. */
  private String lastMail(String to, String subjectPart) throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    String base = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
    String query =
        URLEncoder.encode("to:" + to + " subject:\"" + subjectPart + "\"", StandardCharsets.UTF_8);
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      JsonNode found =
          json.readTree(
              http.send(
                      HttpRequest.newBuilder(URI.create(base + "/api/v1/search?query=" + query))
                          .build(),
                      HttpResponse.BodyHandlers.ofString())
                  .body());
      if (!found.path("messages").isEmpty()) {
        String mid = found.path("messages").get(0).path("ID").asString();
        return json.readTree(
                http.send(
                        HttpRequest.newBuilder(URI.create(base + "/api/v1/message/" + mid)).build(),
                        HttpResponse.BodyHandlers.ofString())
                    .body())
            .path("Text")
            .asString();
      }
      Thread.sleep(150);
    }
    return null;
  }
}
