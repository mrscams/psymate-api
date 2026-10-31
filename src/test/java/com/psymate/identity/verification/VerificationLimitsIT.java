package com.psymate.identity.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Limites et états verrouillés du dossier : plafond de 20, document purgé, états non éditables. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VerificationTestConfig.class)
class VerificationLimitsIT {

  private static final byte[] PDF = "%PDF-1.4\ncontenu".getBytes(StandardCharsets.US_ASCII);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DocumentCipher cipher;

  private UUID tenant;

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  private UUID practitioner(String status) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'coach', ?)",
        id,
        tenant,
        email,
        email,
        status);
    return id;
  }

  private RequestPostProcessor as(UUID id, String status) {
    CurrentPractitioner principal =
        new CurrentPractitioner(id, tenant, id + "@example.test", "Dr Test", "coach", status);
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private UUID insertDocument(UUID practitioner, String type, boolean purged) {
    UUID doc = UUID.randomUUID();
    jdbc.update(
        "insert into verification_document (id, practitioner_id, type, title, storage_key, mime,"
            + " size_bytes, sha256, purged_at) values (?, ?, ?, 'Titre', ?, 'application/pdf', 10,"
            + " 'abc', ?)",
        doc,
        practitioner,
        type,
        "verification/" + practitioner + "/" + doc + ".bin",
        purged ? Timestamp.from(Instant.now()) : null);
    return doc;
  }

  @Test
  void documentLimitIs20() throws Exception {
    UUID id = practitioner("docs_pending");
    for (int i = 0; i < VerificationService.MAX_DOCUMENTS; i++) {
      insertDocument(id, "other", false);
    }
    mvc.perform(
            multipart("/api/me/verification/documents")
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
                .param("type", "other")
                .param("title", "Un de trop")
                .with(as(id, "docs_pending")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_LIMIT"));
  }

  @Test
  void purgedDocumentIs410OnUrlAndFile() throws Exception {
    UUID id = practitioner("docs_pending");
    RequestPostProcessor me = as(id, "docs_pending");
    UUID doc = insertDocument(id, "diploma", true);
    mvc.perform(get("/api/me/verification/documents/" + doc + "/url").with(me))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.code").value("DOCUMENT_PURGED"));
    long exp = Instant.now().plusSeconds(60).getEpochSecond();
    String sig = cipher.sign(doc + "|" + id + "|" + exp);
    mvc.perform(
            get("/api/me/verification/documents/" + doc + "/file?exp=" + exp + "&sig=" + sig)
                .with(me))
        .andExpect(status().isGone());
    mvc.perform(get("/api/me/verification").with(me))
        .andExpect(jsonPath("$.documents[0].purged").value(true))
        .andExpect(jsonPath("$.missing[0]").value("diploma"));
  }

  @Test
  void fileWithoutExpAndSigIs403() throws Exception {
    UUID id = practitioner("docs_pending");
    UUID doc = insertDocument(id, "diploma", false);
    mvc.perform(get("/api/me/verification/documents/" + doc + "/file").with(as(id, "docs_pending")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SIGNED_URL_INVALID"));
  }

  @Test
  void deleteAndSubmitAreRefusedOutsideEditableStates() throws Exception {
    for (String state : new String[] {"under_review", "approved"}) {
      UUID id = practitioner(state);
      RequestPostProcessor me = as(id, state);
      UUID doc = insertDocument(id, "diploma", false);
      mvc.perform(delete("/api/me/verification/documents/" + doc).with(me))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"));
      mvc.perform(post("/api/me/verification/submit").with(me))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"));
      assertThat(
              jdbc.queryForObject(
                  "select verification_status from practitioner where id = ?", String.class, id))
          .isEqualTo(state);
      assertThat(
              jdbc.queryForObject(
                  "select deleted_at is null from verification_document where id = ?",
                  Boolean.class,
                  doc))
          .isTrue();
    }
  }
}
