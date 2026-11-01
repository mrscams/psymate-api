package com.psymate.clinical.export;

import com.psymate.clinical.export.ExportJobRepository.ExportJobView;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.EncryptedStorageFacade;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.api.PractitionerFacade.PractitionerView;
import com.psymate.patients.api.PatientFacade;
import com.psymate.patients.api.PatientRef;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Travail d'export du compte (fiche 27, G Q18), exécuté hors requête par le pool privé de {@link
 * ExportJobService}. Charge les patients du praticien, construit un zip en mémoire ({@code
 * patients.md} puis, par patient, {@code patients/nom-xxxxxxxx.md} et {@code .pdf}, le même rendu
 * que l'export d'un patient), le chiffre et le stocke sous {@code exports/<jobId>.zip}.
 *
 * <p>Il n'y a pas de session ici : le praticien est connu par le job. Les catalogues d'émotions et
 * de tags de clinical sont ceux du praticien courant ; le travail pose donc un principal sur son
 * propre fil, retiré en sortie. L'export est en français (langue par défaut du produit). Tout échec
 * passe le job en {@code failed} avec le message tronqué à 500 caractères, sans trace d'appels.
 */
@Component
class ExportJobRunner {

  static final int MAX_ERROR = 500;

  private static final Logger LOG = LoggerFactory.getLogger(ExportJobRunner.class);
  private static final Locale LOCALE = Locale.FRENCH;

  private final ExportJobRepository jobs;
  private final PatientFacade patients;
  private final PractitionerFacade practitioners;
  private final PatientExportService exports;
  private final EncryptedStorageFacade storage;
  private final Clock clock;

  ExportJobRunner(
      ExportJobRepository jobs,
      PatientFacade patients,
      PractitionerFacade practitioners,
      PatientExportService exports,
      EncryptedStorageFacade storage,
      Clock clock) {
    this.jobs = jobs;
    this.patients = patients;
    this.practitioners = practitioners;
    this.exports = exports;
    this.storage = storage;
    this.clock = clock;
  }

  /** Exécute le job : queued, running, puis done ou failed. Ne lève jamais. */
  void run(UUID jobId) {
    ExportJobView job = jobs.find(jobId).orElse(null);
    if (job == null || !jobs.markRunning(jobId)) {
      return;
    }
    String key = "exports/" + jobId + ".zip";
    boolean stored = false;
    try {
      authenticateAs(job.practitionerId());
      byte[] zip = buildZip(job.practitionerId());
      storage.put(key, zip);
      stored = true;
      Instant now = clock.instant();
      jobs.markDone(jobId, key, now, now.plus(ExportJobService.RETENTION));
    } catch (Exception e) {
      LOG.error("account export {} failed", jobId, e);
      // Pas de fichier orphelin : un zip stocké dont la ligne n'a pas pu passer en done ne serait
      // jamais purgé (storage_key nul). Puis la ligne passe en failed ; si cette écriture échoue
      // aussi, on journalise : le job sera requalifié « interrupted » au bout d'une heure.
      if (stored) {
        try {
          storage.delete(key);
        } catch (RuntimeException cleanup) {
          LOG.error("account export {} : orphan file {} could not be deleted", jobId, key, cleanup);
        }
      }
      try {
        jobs.markFailed(
            jobId,
            truncate(e.getMessage() == null ? e.toString() : e.getMessage()),
            clock.instant());
      } catch (RuntimeException update) {
        LOG.error("account export {} could not be marked failed", jobId, update);
      }
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  static String truncate(String message) {
    return message.length() <= MAX_ERROR ? message : message.substring(0, MAX_ERROR);
  }

  private void authenticateAs(UUID practitionerId) {
    PractitionerView view =
        practitioners
            .find(practitionerId)
            .orElseThrow(() -> new IllegalStateException("praticien introuvable"));
    CurrentPractitioner principal =
        new CurrentPractitioner(
            view.id(),
            view.tenantId(),
            practitioners.emailOf(practitionerId).orElse(""),
            "export",
            view.profession(),
            view.verificationStatus());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    SecurityContextHolder.setContext(context);
  }

  /** Zip en mémoire : patients.md d'abord, puis les fichiers de chaque patient. */
  byte[] buildZip(UUID practitionerId) throws IOException {
    ExportTexts texts = ExportTexts.of(LOCALE);
    Map<String, byte[]> files = new LinkedHashMap<>();
    List<String> lines = new ArrayList<>();
    for (PatientRef patient : patients.ownedBy(practitionerId)) {
      ExportDocument doc = exports.build(patient.id(), LOCALE);
      String base =
          "patients/"
              + PatientExportService.slug(patient.displayName())
              + "-"
              + patient.id().toString().substring(0, 8);
      files.put(base + ".md", exports.render(doc, ExportFormat.MD, LOCALE).content());
      files.put(base + ".pdf", exports.render(doc, ExportFormat.PDF, LOCALE).content());
      lines.add(
          texts.get(
              "account.line",
              markdownEscape(patient.displayName())
                  + (patient.archived() ? " " + texts.get("account.archived") : ""),
              patient.birthDate() == null
                  ? texts.get("account.birth.unknown")
                  : ExportDates.day(patient.birthDate()),
              String.valueOf(doc.entryCount()),
              base + ".md",
              base + ".pdf"));
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      add(zip, "patients.md", index(texts, lines).getBytes(StandardCharsets.UTF_8));
      for (Map.Entry<String, byte[]> file : files.entrySet()) {
        add(zip, file.getKey(), file.getValue());
      }
    }
    return bytes.toByteArray();
  }

  private String index(ExportTexts texts, List<String> lines) {
    StringBuilder sb = new StringBuilder();
    sb.append("# ").append(texts.get("account.title")).append("\n\n");
    sb.append(
            texts.get(
                "account.exportedOn",
                ExportDates.day(java.time.LocalDate.now(clock.withZone(java.time.ZoneOffset.UTC)))))
        .append("\n\n");
    if (lines.isEmpty()) {
      sb.append(texts.get("account.empty")).append('\n');
    }
    for (String line : lines) {
      sb.append("- ").append(line).append('\n');
    }
    return sb.toString();
  }

  private static void add(ZipOutputStream zip, String name, byte[] content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content);
    zip.closeEntry();
  }

  private static String markdownEscape(String text) {
    return MarkdownRenderer.escape(text);
  }
}
