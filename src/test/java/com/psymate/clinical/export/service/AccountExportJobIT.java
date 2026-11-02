package com.psymate.clinical.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.clinical.export.ExportTestSupport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * M6-05 : export du compte (fiche 27, G Q18) de bout en bout : un compte suspendu lance son export
 * par identifiants, suit le job, télécharge le zip chiffré puis le lien expire (410) et le fichier
 * est purgé. Contexte complet, vraie base, jamais de mock.
 */
class AccountExportJobIT extends ExportTestSupport {

  @Autowired private ExportJobService jobs;
  @Autowired private ExportPurgeJob purgeJob;

  @Value("${app.storage.dir}")
  private String storageDir;

  private UUID doctor;
  private UUID alice;

  @BeforeEach
  void setUp() throws Exception {
    doctor = account("Dr Suspendu", "approved");
    alice = patient(doctor, "Alice Martin", LocalDate.of(1985, 6, 12));
    patient(doctor, "Bob Durand", null);
    UUID archived = patient(doctor, "Zoé Archivée", null);
    jdbc.update("update patient set archived_at = now() where id = ?", archived);

    // Données saisies pendant que le compte était actif.
    String s = startSession(doctor, alice, Instant.parse("2026-09-01T09:00:00Z"));
    createEntry(
        doctor,
        alice,
        s,
        "{\"type\":\"note\",\"title\":\"Titre clinique A\",\"body\":\"Corps clinique A\"}");
    createEntry(
        doctor,
        alice,
        s,
        "{\"type\":\"note\",\"title\":\"Titre prive A\",\"body\":\"Corps prive A\","
            + "\"visibility\":\"private\"}");
    closeSession(doctor, s, "Compte rendu A");
    clock.set(Instant.now());
  }

  // ---- helpers

  private void suspend() {
    jdbc.update("update practitioner set verification_status = 'suspended' where id = ?", doctor);
  }

  private ResultActions requestExport(String email, String password) throws Exception {
    String ip = freshIp();
    String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    return mvc.perform(
        MockMvcRequestBuilders.post("/api/me/export")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                }));
  }

  /** Attend que le job soit terminé, en le suivant par son statusUrl (jeton, sans session). */
  private JsonNodeLike awaitStatus(String statusUrl, String expected) {
    return await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofMillis(200))
        .until(
            () -> {
              String json = body(get(statusUrl, null));
              return new JsonNodeLike(json);
            },
            node -> expected.equals(node.str("status")) || "failed".equals(node.str("status")));
  }

  /** Lecture minimale d'un JSON de réponse. */
  private record JsonNodeLike(String json) {
    String str(String field) {
      Object v = JsonPath.read(json, "$." + field);
      return v == null ? null : v.toString();
    }
  }

  private Map<String, byte[]> unzip(byte[] zip) throws IOException {
    Map<String, byte[]> files = new LinkedHashMap<>();
    try (ZipInputStream in =
        new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
      for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
        files.put(e.getName(), in.readAllBytes());
      }
    }
    return files;
  }

  private String text(Map<String, byte[]> files, String name) {
    return new String(files.get(name), StandardCharsets.UTF_8);
  }

  private String shortId(UUID id) {
    return id.toString().substring(0, 8);
  }

  private Path storedFile(String jobId) {
    return Path.of(storageDir).toAbsolutePath().normalize().resolve("exports/" + jobId + ".zip");
  }

  /** Lance l'export du compte suspendu et attend sa fin ; renvoie (jobId, statusUrl, statut). */
  private record Done(String jobId, String statusUrl, JsonNodeLike status) {}

  private Done suspendedExportDone() throws Exception {
    suspend();
    MvcResult accepted =
        requestExport(emailOf(doctor), PASSWORD)
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobId").isNotEmpty())
            .andReturn();
    String json = accepted.getResponse().getContentAsString();
    String jobId = JsonPath.read(json, "$.jobId");
    String statusUrl = JsonPath.read(json, "$.statusUrl");
    return new Done(jobId, statusUrl, awaitStatus(statusUrl, "done"));
  }

  // ---- 1. compte suspendu : export, suivi, téléchargement

  @Test
  void suspendedAccountRequestsFollowsAndDownloadsItsExport() throws Exception {
    suspend();
    // Le login est bien refusé : pas de session possible pour ce compte.
    mvc.perform(
            MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"" + emailOf(doctor) + "\",\"password\":\"" + PASSWORD + "\"}")
                .with(
                    r -> {
                      r.setRemoteAddr(freshIp());
                      return r;
                    }))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));

    ResultActions accepted = requestExport(emailOf(doctor), PASSWORD);
    MvcResult result =
        accepted
            .andExpect(status().isAccepted())
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andReturn();
    String json = result.getResponse().getContentAsString();
    String jobId = JsonPath.read(json, "$.jobId");
    String statusUrl = JsonPath.read(json, "$.statusUrl");
    assertThat(statusUrl).startsWith("/api/me/export/" + jobId + "?token=");

    JsonNodeLike done = awaitStatus(statusUrl, "done");
    assertThat(done.str("status")).isEqualTo("done");
    assertThat(done.str("expiresAt")).isNotNull();
    assertThat(done.str("finishedAt")).isNotNull();
    String downloadUrl = done.str("downloadUrl");
    assertThat(downloadUrl).startsWith("/api/me/export/" + jobId + "/file?exp=");

    // La ligne : fichier chiffré sous exports/, valable 7 jours exactement.
    assertThat(
            jdbc.queryForObject(
                "select storage_key from export_job where id = ?::uuid", String.class, jobId))
        .isEqualTo("exports/" + jobId + ".zip");
    assertThat(
            jdbc.queryForObject(
                "select extract(epoch from expires_at - finished_at)::int from export_job"
                    + " where id = ?::uuid",
                Integer.class,
                jobId))
        .isEqualTo(7 * 24 * 3600);
    byte[] onDisk = Files.readAllBytes(storedFile(jobId));
    assertThat(new String(onDisk, 0, 2, StandardCharsets.ISO_8859_1))
        .as("le zip est chiffré sur le disque")
        .isNotEqualTo("PK");

    // Téléchargement sans session, par l'URL signée.
    String day =
        DateTimeFormatter.BASIC_ISO_DATE.format(
            clock.instant().atOffset(ZoneOffset.UTC).toLocalDate());
    byte[] zip =
        get(downloadUrl, null)
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/zip"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"export-psymate-" + day + ".zip\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    Map<String, byte[]> files = unzip(zip);
    String aliceBase = "patients/alice-martin-" + shortId(alice);
    assertThat(files.keySet()).contains("patients.md", aliceBase + ".md", aliceBase + ".pdf");
    assertThat(files.keySet().stream().filter(n -> n.endsWith(".md") && n.startsWith("patients/")))
        .hasSize(3);
    assertThat(files.keySet().stream().filter(n -> n.endsWith(".pdf"))).hasSize(3);
    assertThat(new String(files.get(aliceBase + ".pdf"), 0, 5, StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");

    String index = text(files, "patients.md");
    assertThat(index).contains("Alice Martin").contains("Bob Durand").contains(aliceBase + ".md");
    // G Q18 : tous les patients, archivés compris (seule sortie des données d'un compte refusé).
    assertThat(index).contains("Zoé Archivée (archivé)");
    assertThat(index).contains("12/06/1985");

    String md = text(files, aliceBase + ".md");
    assertThat(md).contains("Titre clinique A").contains("Compte rendu A");
    assertThat(md).doesNotContain("Titre prive A").doesNotContain("Corps prive A");
    assertThat(md).doesNotContain("contact-secret");
  }

  // ---- 2. compte approuvé, un seul job à la fois

  @Test
  void approvedConnectedAccountExportsWithItsPrincipal() throws Exception {
    MvcResult accepted =
        post("/api/me/export", null, as(doctor)).andExpect(status().isAccepted()).andReturn();
    String json = accepted.getResponse().getContentAsString();
    String jobId = JsonPath.read(json, "$.jobId");

    // Le principal suit son job sans jeton.
    JsonNodeLike done =
        await()
            .atMost(Duration.ofSeconds(90))
            .pollInterval(Duration.ofMillis(200))
            .until(
                () -> new JsonNodeLike(body(get("/api/me/export/" + jobId, as(doctor)))),
                n -> "done".equals(n.str("status")) || "failed".equals(n.str("status")));
    assertThat(done.str("status")).isEqualTo("done");
    get(done.str("downloadUrl"), null).andExpect(status().isOk());

    // Un export terminé ne bloque pas le suivant.
    post("/api/me/export", null, as(doctor)).andExpect(status().isAccepted());
  }

  @Test
  void approvedAccountWithoutSessionNeedsCredentialsAndIsNotAnAppealCase() throws Exception {
    // Ni session ni corps : 422 sur email et password.
    post("/api/me/export", null, null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[?(@.field=='email')].code").value("REQUIRED"))
        .andExpect(jsonPath("$.errors[?(@.field=='password')].code").value("REQUIRED"));
    // Identifiants d'un compte approuvé sans session : 409, il doit se connecter.
    requestExport(emailOf(doctor), PASSWORD)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_SUSPENDED"));
    assertThat(count("select count(*) from export_job where practitioner_id = ?", doctor)).isZero();
  }

  @Test
  void secondExportWhileOneIsRunningIs409() throws Exception {
    jdbc.update(
        "insert into export_job (id, practitioner_id, status) values (?, ?, 'running')",
        UUID.randomUUID(),
        doctor);
    post("/api/me/export", null, as(doctor))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("EXPORT_IN_PROGRESS"));
    suspend();
    requestExport(emailOf(doctor), PASSWORD)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EXPORT_IN_PROGRESS"));

    // Un job queued bloque de la même façon ; un job d'un autre praticien ne bloque pas.
    jdbc.update("update export_job set status = 'queued' where practitioner_id = ?", doctor);
    post("/api/me/export", null, as(doctor)).andExpect(status().isConflict());
    UUID other = account("Dr Autre", "approved");
    post("/api/me/export", null, as(other)).andExpect(status().isAccepted());
  }

  @Test
  void aJobInterruptedLongAgoNoLongerBlocksNewRequests() throws Exception {
    UUID stale = UUID.randomUUID();
    jdbc.update(
        "insert into export_job (id, practitioner_id, status, requested_at)"
            + " values (?, ?, 'running', ?)",
        stale,
        doctor,
        Timestamp.from(clock.instant().minus(Duration.ofHours(3))));
    post("/api/me/export", null, as(doctor)).andExpect(status().isAccepted());
    assertThat(
            jdbc.queryForObject(
                "select status || ':' || error from export_job where id = ?", String.class, stale))
        .isEqualTo("failed:interrupted");
  }

  @Test
  void twoFactorAccountGetsOtpRequiredWithoutCodeAndNoJobIsCreated() throws Exception {
    suspend();
    jdbc.update(
        "update practitioner set totp_secret_enc = ?, totp_enabled_at = now() where id = ?",
        new byte[] {1},
        doctor);
    requestExport(emailOf(doctor), PASSWORD)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.otpRequired").value(true))
        .andExpect(jsonPath("$.jobId").doesNotExist());
    assertThat(count("select count(*) from export_job where practitioner_id = ?", doctor)).isZero();
  }

  // ---- 3. authentification par identifiants : le câblage AuthErrorScope

  @Test
  void wrongPasswordIs401InvalidCredentialsAsProblemJson() throws Exception {
    suspend();
    requestExport(emailOf(doctor), "mauvais-mot-de-passe")
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    requestExport("inconnu@example.test", PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    assertThat(count("select count(*) from export_job where practitioner_id = ?", doctor)).isZero();
  }

  @Test
  void rejectedAccountCanExportButClosedAccountIs409() throws Exception {
    UUID rejected = account("Dr Refusé", "rejected");
    requestExport(emailOf(rejected), PASSWORD).andExpect(status().isAccepted());

    // Un compte clos n'est ni refusé ni suspendu pour la façade : 409 ACCOUNT_NOT_SUSPENDED.
    UUID closed = account("Dr Clos", "closed");
    requestExport(emailOf(closed), PASSWORD)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_SUSPENDED"));
  }

  // ---- 4. suivi : un job n'est visible que de son praticien ou du porteur du jeton

  @Test
  void anotherPractitionersJobAndForgedTokensAreNotFound() throws Exception {
    Done done = suspendedExportDone();
    UUID other = account("Dr Autre", "approved");
    String jobUrl = "/api/me/export/" + done.jobId();
    String token = done.statusUrl().substring(done.statusUrl().indexOf("token=") + 6);

    // Un autre praticien connecté, sans jeton : 404, jamais 403.
    get(jobUrl, as(other))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("EXPORT_NOT_FOUND"));
    // Ni principal ni jeton, jeton falsifié, jeton d'un autre job : tous 404.
    get(jobUrl, null).andExpect(status().isNotFound());
    get(jobUrl + "?token=" + token.substring(0, token.length() - 2) + "AA", null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("EXPORT_NOT_FOUND"));
    get(jobUrl + "?token=n-importe-quoi", null).andExpect(status().isNotFound());
    get("/api/me/export/" + UUID.randomUUID() + "?token=" + token, null)
        .andExpect(status().isNotFound());
    // Le jeton valable ouvre le statut, y compris pour un principal étranger (jeton porteur).
    get(jobUrl + "?token=" + token, as(other)).andExpect(status().isOk());
    get(jobUrl + "?token=" + token, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value(done.jobId()))
        .andExpect(jsonPath("$.status").value("done"))
        .andExpect(jsonPath("$.requestedAt").isNotEmpty());
  }

  // ---- 5. URL signée, expiration, purge

  @Test
  void tamperedSignedUrlsAreForbidden() throws Exception {
    Done done = suspendedExportDone();
    String url = done.status().str("downloadUrl");
    String base = "/api/me/export/" + done.jobId() + "/file";
    String exp = url.substring(url.indexOf("exp=") + 4, url.indexOf("&sig="));
    String sig = url.substring(url.indexOf("&sig=") + 5);

    get(base + "?exp=" + exp + "&sig=" + sig.substring(0, sig.length() - 2) + "AA", null)
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("SIGNED_URL_INVALID"));
    // Échéance prolongée : la signature ne couvre plus l'échéance demandée.
    get(base + "?exp=" + (Long.parseLong(exp) + 86400) + "&sig=" + sig, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SIGNED_URL_INVALID"));
    get(base + "?exp=abc&sig=" + sig, null).andExpect(status().isForbidden());
    get(base + "?exp=" + exp, null).andExpect(status().isForbidden());
    get("/api/me/export/" + UUID.randomUUID() + "/file?exp=" + exp + "&sig=" + sig, null)
        .andExpect(status().isForbidden());
    get(url, null).andExpect(status().isOk());
  }

  @Test
  void linkExpiresAfterSevenDaysThenFileIsPurgedAndLineKept() throws Exception {
    Done done = suspendedExportDone();
    String downloadUrl = done.status().str("downloadUrl");
    assertThat(Files.exists(storedFile(done.jobId()))).isTrue();

    // Avant l'échéance : rien à purger, le lien sert.
    assertThat(jobs.purgeDue()).isZero();
    get(downloadUrl, null).andExpect(status().isOk());

    // Après 7 jours : 410, plus de lien dans le statut ; le fichier existe encore jusqu'à la purge.
    clock.advance(Duration.ofDays(7).plusMinutes(1));
    get(downloadUrl, null)
        .andExpect(status().isGone())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("EXPORT_EXPIRED"));
    get(done.statusUrl(), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("done"))
        .andExpect(jsonPath("$.downloadUrl").value((Object) null));
    assertThat(Files.exists(storedFile(done.jobId()))).isTrue();

    // Purge quotidienne : fichier supprimé, ligne conservée avec purged_at. La base est partagée
    // entre les tests : d'autres exports terminés expirent aussi.
    assertThat(jobs.purgeDue()).isGreaterThanOrEqualTo(1);
    assertThat(Files.exists(storedFile(done.jobId()))).isFalse();
    assertThat(
            count(
                "select count(*) from export_job where id = ?::uuid and purged_at is not null"
                    + " and status = 'done' and storage_key is not null",
                done.jobId()))
        .isEqualTo(1);
    get(downloadUrl, null).andExpect(status().isGone());
    // Idempotent.
    assertThat(jobs.purgeDue()).isZero();
  }

  @Test
  void purgeJobIsScheduledOnItsOwnPrivateSchedulerAtThreeFortyFiveUtc() {
    assertThat(ExportPurgeJob.CRON).isEqualTo("0 45 3 * * *");
    assertThat(purgeJob).isNotNull();
    // run() n'échoue jamais, même sans rien à purger.
    purgeJob.run();
  }

  @Test
  void expiredJobIsPurgedByTheJobRunEntryPoint() throws Exception {
    Done done = suspendedExportDone();
    jdbc.update(
        "update export_job set expires_at = now() - interval '1 minute' where id = ?::uuid",
        done.jobId());
    clock.set(Instant.now().plusSeconds(5));
    purgeJob.run();
    assertThat(Files.exists(storedFile(done.jobId()))).isFalse();
    assertThat(
            count(
                "select count(*) from export_job where id = ?::uuid and purged_at is not null",
                done.jobId()))
        .isEqualTo(1);
  }

  @Test
  void errorMessageIsTruncatedTo500Characters() {
    assertThat(ExportJobRunner.truncate("x".repeat(900))).hasSize(500);
    assertThat(ExportJobRunner.truncate("court")).isEqualTo("court");
  }

  @Test
  void statusStartsWith202AndTokenUrlIsRelative() throws Exception {
    suspend();
    requestExport(emailOf(doctor), PASSWORD)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.statusUrl").value(startsWith("/api/me/export/")));
  }
}
