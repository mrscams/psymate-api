package com.psymate.identity.verification;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.VerificationSubmitted;
import com.psymate.identity.verification.VerificationDtos.AddressDto;
import com.psymate.identity.verification.VerificationDtos.DecisionDto;
import com.psymate.identity.verification.VerificationDtos.DocumentDto;
import com.psymate.identity.verification.VerificationDtos.ProfessionalInfoDto;
import com.psymate.identity.verification.VerificationDtos.ProfessionalInfoRequest;
import com.psymate.identity.verification.VerificationDtos.SignedUrlDto;
import com.psymate.identity.verification.VerificationDtos.VerificationDto;
import com.psymate.identity.verification.VerificationException.FieldError;
import com.psymate.identity.verification.VerificationRepository.AccountRow;
import com.psymate.identity.verification.VerificationRepository.DocumentRow;
import com.psymate.identity.verification.VerificationRepository.DuePurge;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Dossier professionnel du praticien connecté (A10.2, A10.3, fiche 13). */
@Service
class VerificationService {

  private static final Logger LOG = LoggerFactory.getLogger(VerificationService.class);

  static final Duration URL_VALIDITY = Duration.ofMinutes(5);
  static final int MAX_DOCUMENTS = 20;
  static final int PURGE_BATCH = 500;

  private static final Set<String> TYPES =
      Set.of("diploma", "registration", "certification", "professional_card", "identity", "other");

  /** États dans lesquels le praticien peut modifier son dossier (A10.3). */
  private static final Set<String> EDITABLE = Set.of("docs_pending", "needs_more");

  private static final Pattern PHONE = Pattern.compile("^[0-9+ ().-]{6,30}$");
  private static final Pattern PRO_NUMBER = Pattern.compile("^[A-Za-z0-9 -]{4,20}$");
  private static final Pattern IP = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

  /** Fichier déchiffré prêt à être servi. */
  record FileContent(byte[] bytes, String mime) {}

  private final VerificationRepository repo;
  private final StorageService storage;
  private final DocumentCipher cipher;
  private final VerificationMailer mailer;
  private final DomainEventPublisher events;
  private final Clock clock;
  private final ExecutorService mailExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "verification-mail");
            t.setDaemon(true);
            return t;
          });

  VerificationService(
      VerificationRepository repo,
      StorageService storage,
      DocumentCipher cipher,
      VerificationMailer mailer,
      DomainEventPublisher events,
      Clock clock) {
    this.repo = repo;
    this.storage = storage;
    this.cipher = cipher;
    this.mailer = mailer;
    this.events = events;
    this.clock = clock;
  }

  @PreDestroy
  void shutdown() {
    mailExecutor.shutdown();
  }

  // ---- lecture

  @Transactional(readOnly = true)
  VerificationDto get(UUID practitionerId) {
    AccountRow account = account(practitionerId);
    List<DocumentRow> docs = repo.activeDocuments(practitionerId);
    List<String> present = presentTypes(docs);
    List<DecisionDto> decisions =
        repo.decisionMessages(practitionerId).stream()
            .map(d -> new DecisionDto(d.kind(), d.message(), d.decidedAt()))
            .toList();
    return new VerificationDto(
        account.status(),
        account.profession(),
        docs.stream().map(VerificationService::toDto).toList(),
        CompletenessRules.missing(account.profession(), present),
        CompletenessRules.recommended(account.profession(), present),
        repo.lastSubmittedAt(practitionerId).orElse(null),
        decisions,
        info(account));
  }

  @Transactional(readOnly = true)
  DocumentDto getDocument(UUID practitionerId, UUID documentId) {
    return toDto(owned(practitionerId, documentId));
  }

  // ---- dépôt et retrait

  @Transactional
  DocumentDto upload(
      UUID practitionerId,
      String type,
      String title,
      String issuer,
      String yearText,
      byte[] content) {
    Integer year = validateMetadata(type, title, issuer, yearText);
    AccountRow account = lock(practitionerId);
    requireEditable(account);
    if (repo.activeDocuments(practitionerId).size() >= MAX_DOCUMENTS) {
      throw VerificationException.of(
          HttpStatus.CONFLICT, "DOCUMENT_LIMIT", "verification.error.limit");
    }
    DocumentSanitizer.Clean clean = DocumentSanitizer.clean(content);
    UUID id = UUID.randomUUID();
    String key = "verification/" + practitionerId + "/" + id + ".bin";
    storage.put(key, cipher.encrypt(clean.bytes(), key));
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              quietDelete(key);
            }
          }
        });
    Instant now = clock.instant();
    repo.insertDocument(
        id,
        practitionerId,
        type,
        title.trim(),
        blankToNull(issuer),
        year,
        key,
        clean.mime(),
        clean.bytes().length,
        sha256(clean.bytes()),
        now);
    return toDto(repo.findDocument(id).orElseThrow());
  }

  @Transactional
  void delete(UUID practitionerId, UUID documentId) {
    DocumentRow doc = owned(practitionerId, documentId);
    requireEditable(lock(practitionerId));
    repo.markDeleted(doc.id(), clock.instant());
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            quietDelete(doc.storageKey());
          }
        });
  }

  // ---- lecture du fichier par URL signée

  @Transactional(readOnly = true)
  SignedUrlDto signedUrl(UUID practitionerId, UUID documentId) {
    DocumentRow doc = owned(practitionerId, documentId);
    if (doc.purgedAt() != null) {
      throw purged();
    }
    long exp = clock.instant().plus(URL_VALIDITY).getEpochSecond();
    String sig = cipher.sign(payload(documentId, practitionerId, exp));
    return new SignedUrlDto(
        "/api/me/verification/documents/" + documentId + "/file?exp=" + exp + "&sig=" + sig,
        Instant.ofEpochSecond(exp));
  }

  /**
   * Sert le fichier déchiffré : praticien propriétaire (403 sinon), signature et échéance valides.
   * Chaque lecture écrit une ligne document_access_log.
   */
  @Transactional
  FileContent file(UUID practitionerId, UUID documentId, Long exp, String sig, String ip) {
    DocumentRow doc = owned(practitionerId, documentId);
    Instant now = clock.instant();
    boolean valid =
        exp != null
            && sig != null
            && exp >= now.getEpochSecond()
            && MessageDigest.isEqual(
                cipher
                    .sign(payload(documentId, practitionerId, exp))
                    .getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    if (!valid) {
      throw VerificationException.of(
          HttpStatus.FORBIDDEN, "SIGNED_URL_INVALID", "verification.error.url");
    }
    if (doc.purgedAt() != null) {
      throw purged();
    }
    byte[] plain = cipher.decrypt(storage.get(doc.storageKey()), doc.storageKey());
    repo.insertAccessLog(doc.id(), practitionerId, normalizeIp(ip), now);
    return new FileContent(plain, doc.mime());
  }

  // ---- soumission

  @Transactional
  VerificationDto submit(UUID practitionerId) {
    AccountRow account = lock(practitionerId);
    requireEditable(account);
    List<String> missing =
        CompletenessRules.missing(
            account.profession(), presentTypes(repo.activeDocuments(practitionerId)));
    if (!missing.isEmpty()) {
      throw VerificationException.incomplete(missing);
    }
    Instant now = clock.instant();
    repo.setStatus(practitionerId, "under_review", now);
    repo.insertSecurityEvent(practitionerId, "verification_submitted", now);
    events.publish(
        new VerificationSubmitted(account.tenantId(), practitionerId, account.profession()));
    notifyAdminsAfterCommit(account);
    return get(practitionerId);
  }

  // ---- informations professionnelles

  @Transactional
  ProfessionalInfoDto updateProfessionalInfo(UUID practitionerId, ProfessionalInfoRequest r) {
    AccountRow a = lock(practitionerId);
    List<FieldError> errors = new ArrayList<>();
    String pro =
        merge(
            a.professionalNumber(),
            r.professionalNumber(),
            PRO_NUMBER,
            "professionalNumber",
            errors);
    String phone = merge(a.phone(), r.phone(), PHONE, "phone", errors);
    String street = a.street();
    String postal = a.postalCode();
    String city = a.city();
    if (r.address() != null) {
      street = merge(street, r.address().street(), null, "address.street", errors);
      postal = merge(postal, r.address().postalCode(), null, "address.postalCode", errors);
      city = merge(city, r.address().city(), null, "address.city", errors);
    }
    if (!errors.isEmpty()) {
      throw VerificationException.fields(errors);
    }
    boolean remote = r.remoteOnly() != null ? r.remoteOnly() : a.remoteOnly();
    repo.updateProfessionalInfo(
        practitionerId, pro, phone, street, postal, city, remote, clock.instant());
    return info(account(practitionerId));
  }

  // ---- purge planifiée

  /**
   * Supprime les fichiers dont purge_after est dépassé (posé par la décision, +90 jours) et pose
   * purged_at ; la ligne et ses métadonnées restent. Idempotent.
   */
  int purgeDue() {
    Instant now = clock.instant();
    int total = 0;
    // Un seul lot par exécution : une ligne en échec reste due et sera retentée le lendemain,
    // sans boucle infinie ni blocage des lignes suivantes.
    for (DuePurge d : repo.duePurges(now, PURGE_BATCH)) {
      try {
        storage.delete(d.storageKey());
        repo.markPurged(d.id(), now);
        total++;
      } catch (RuntimeException e) {
        LOG.error("could not purge verification document {}", d.id(), e);
      }
    }
    return total;
  }

  // ---- utilitaires

  private AccountRow account(UUID id) {
    return repo.findAccount(id).orElseThrow(VerificationService::notFound);
  }

  private AccountRow lock(UUID id) {
    return repo.lockAccount(id).orElseThrow(VerificationService::notFound);
  }

  private static void requireEditable(AccountRow account) {
    if (!EDITABLE.contains(account.status())) {
      throw VerificationException.of(
          HttpStatus.CONFLICT, "VERIFICATION_LOCKED", "verification.error.locked");
    }
  }

  /** Document du praticien : 404 s'il n'existe pas ou a été retiré, 403 s'il est à un autre. */
  private DocumentRow owned(UUID practitionerId, UUID documentId) {
    DocumentRow doc =
        repo.findDocument(documentId)
            .filter(d -> d.deletedAt() == null)
            .orElseThrow(
                () ->
                    VerificationException.of(
                        HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "verification.error.notfound"));
    if (!doc.practitionerId().equals(practitionerId)) {
      throw VerificationException.of(
          HttpStatus.FORBIDDEN, "DOCUMENT_FORBIDDEN", "verification.error.forbidden");
    }
    return doc;
  }

  private static VerificationException notFound() {
    return VerificationException.of(
        HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "verification.error.notfound");
  }

  private static VerificationException purged() {
    return VerificationException.of(
        HttpStatus.GONE, "DOCUMENT_PURGED", "verification.error.purged");
  }

  private static List<String> presentTypes(List<DocumentRow> docs) {
    return docs.stream()
        .filter(d -> d.purgedAt() == null)
        .map(DocumentRow::type)
        .distinct()
        .toList();
  }

  private static String payload(UUID documentId, UUID practitionerId, long exp) {
    return documentId + "|" + practitionerId + "|" + exp;
  }

  private static DocumentDto toDto(DocumentRow d) {
    return new DocumentDto(
        d.id(),
        d.type(),
        d.title(),
        d.issuer(),
        d.year(),
        d.mime(),
        d.sizeBytes(),
        d.uploadedAt(),
        d.reviewedStatus(),
        d.purgedAt() != null);
  }

  private static ProfessionalInfoDto info(AccountRow a) {
    return new ProfessionalInfoDto(
        a.professionalNumber(),
        a.phone(),
        new AddressDto(a.street(), a.postalCode(), a.city()),
        a.remoteOnly());
  }

  private static Integer validateMetadata(
      String type, String title, String issuer, String yearText) {
    List<FieldError> errors = new ArrayList<>();
    if (type == null || !TYPES.contains(type)) {
      errors.add(new FieldError("type", "INVALID_TYPE", "verification.error.type"));
    }
    if (title == null || title.isBlank() || title.length() > 200) {
      errors.add(new FieldError("title", "INVALID_TITLE", "verification.error.title"));
    }
    if (issuer != null && issuer.length() > 200) {
      errors.add(new FieldError("issuer", "INVALID_ISSUER", "verification.error.issuer"));
    }
    Integer year = null;
    if (yearText != null && !yearText.isBlank()) {
      try {
        year = Integer.valueOf(yearText.trim());
        if (year < 1950 || year > 2100) {
          throw new NumberFormatException();
        }
      } catch (NumberFormatException e) {
        errors.add(new FieldError("year", "INVALID_YEAR", "verification.error.year"));
      }
    }
    if (!errors.isEmpty()) {
      throw VerificationException.fields(errors);
    }
    return year;
  }

  /** null : inchangé ; vide : effacé ; sinon valeur validée. */
  private static String merge(
      String current, String incoming, Pattern pattern, String field, List<FieldError> errors) {
    if (incoming == null) {
      return current;
    }
    String v = incoming.trim();
    if (v.isEmpty()) {
      return null;
    }
    if ((pattern != null && !pattern.matcher(v).matches()) || v.length() > 200) {
      errors.add(new FieldError(field, "INVALID_FIELD", "verification.error.field"));
      return current;
    }
    return v;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String normalizeIp(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    int zone = s.indexOf('%');
    if (zone >= 0) {
      s = s.substring(0, zone);
    }
    return IP.matcher(s).matches() && (s.contains(".") || s.contains(":")) ? s : null;
  }

  private void quietDelete(String key) {
    try {
      storage.delete(key);
    } catch (RuntimeException e) {
      LOG.error("could not delete stored object {}", key, e);
    }
  }

  private void notifyAdminsAfterCommit(AccountRow account) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            mailExecutor.execute(
                () -> {
                  try {
                    for (var admin : repo.adminRecipients()) {
                      mailer.sendSubmitted(
                          admin.email(),
                          Locale.forLanguageTag(admin.locale() == null ? "fr" : admin.locale()),
                          account.displayName(),
                          account.profession());
                    }
                  } catch (RuntimeException e) {
                    LOG.error("admin notification could not be sent", e);
                  }
                });
          }
        });
  }

  private static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
