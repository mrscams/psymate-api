package com.psymate.directory;

import com.psymate.directory.ContactMailer.ContactMessage;
import com.psymate.directory.ContactRequestRepository.NewRequest;
import com.psymate.directory.ContactRequestRepository.Recipient;
import com.psymate.identity.api.PractitionerFacade;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Formulaire de contact public (F26, UC-15). Un message accepté est enregistré dans contact_request
 * (transaction), puis relayé par e-mail une fois la transaction validée, hors du thread de la
 * requête : le visiteur reçoit 202 dans tous les cas acceptés, et un échec d'envoi ne lui remonte
 * jamais (il est tracé dans relay_error). Le spam évident (honeypot, envoi trop rapide, fiche
 * inconnue ou non publiée) est refusé en silence : même réponse, aucune trace.
 */
@Service
class ContactRequestService {

  static final Duration MIN_FILL_TIME = Duration.ofSeconds(3);
  static final Duration RETENTION = Duration.ofDays(30);
  static final int MAX_RELAY_ERROR = 500;

  private static final Logger LOG = LoggerFactory.getLogger(ContactRequestService.class);

  private final ContactRequestRepository repository;
  private final PractitionerFacade practitioners;
  private final ContactMailer mailer;
  private final IpHasher ipHasher;
  private final TransactionTemplate tx;
  private final Clock clock;
  // Pool privé (pas de TaskExecutor injecté : voir ContactPurgeJob). File bornée, threads daemon.
  private final ThreadPoolExecutor relay =
      new ThreadPoolExecutor(
          2,
          2,
          0L,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(200),
          r -> {
            Thread t = new Thread(r, "contact-relay");
            t.setDaemon(true);
            return t;
          });

  ContactRequestService(
      ContactRequestRepository repository,
      PractitionerFacade practitioners,
      ContactMailer mailer,
      IpHasher ipHasher,
      TransactionTemplate tx,
      Clock clock) {
    this.repository = repository;
    this.practitioners = practitioners;
    this.mailer = mailer;
    this.ipHasher = ipHasher;
    this.tx = tx;
    this.clock = clock;
  }

  /** Spam évident : honeypot rempli, ou formulaire soumis en moins de 3 s (ou dans le futur). */
  static boolean looksLikeSpam(String honeypot, Instant formShownAt, Instant now) {
    if (honeypot != null && !honeypot.isBlank()) {
      return true;
    }
    return formShownAt == null
        || formShownAt.isAfter(now)
        || Duration.between(formShownAt, now).compareTo(MIN_FILL_TIME) < 0;
  }

  /**
   * Traite un envoi déjà validé (Jakarta Validation). Retourne sans erreur dans tous les cas : le
   * contrôleur répond 202 que le message soit relayé ou ignoré en silence.
   */
  void submit(String slug, SubmitContactRequest r, String remoteAddr) {
    Instant now = clock.instant();
    if (looksLikeSpam(r.website(), r.formShownAt(), now)) {
      LOG.debug("contact request ignored (anti-spam)");
      return;
    }
    Optional<Recipient> recipient = repository.findPublishedRecipient(slug);
    if (recipient.isEmpty()) {
      LOG.debug("contact request ignored (unknown or unpublished profile)");
      return;
    }
    UUID practitionerId = recipient.get().practitionerId();
    String to = recipient.get().contactEmail();
    if (to == null || to.isBlank()) {
      to = practitioners.emailOf(practitionerId).orElse(null);
    }
    if (to == null) {
      LOG.debug("contact request ignored (no recipient address)");
      return;
    }
    OffsetDateTime createdAt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
    UUID id = UUID.randomUUID();
    String name = r.name().trim();
    String email = r.email().trim();
    String subject = r.subject() == null || r.subject().isBlank() ? null : r.subject().trim();
    NewRequest row =
        new NewRequest(
            id,
            practitionerId,
            name,
            email,
            subject,
            r.message(),
            createdAt,
            ipHasher.hash(IpBuckets.key(remoteAddr), createdAt.toLocalDate()),
            createdAt,
            createdAt.plus(RETENTION));
    tx.executeWithoutResult(status -> repository.insert(row));
    // Après commit : la ligne existe, l'envoi n'est jamais rattaché à la transaction.
    ContactMessage message = new ContactMessage(to, name, email, subject, r.message());
    try {
      relay.execute(() -> relay(id, practitionerId, message));
    } catch (RejectedExecutionException e) {
      repository.markRelayFailed(id, "relay queue full");
    }
  }

  private void relay(UUID id, UUID practitionerId, ContactMessage message) {
    try {
      mailer.send(message, localeOf(practitionerId));
      repository.markRelayed(id, OffsetDateTime.now(clock));
    } catch (RuntimeException e) {
      LOG.warn("contact relay failed for request {}", id);
      try {
        repository.markRelayFailed(id, truncate(e.toString()));
      } catch (RuntimeException inner) {
        LOG.error("contact relay error could not be recorded for request {}", id, inner);
      }
    }
  }

  private Locale localeOf(UUID practitionerId) {
    Object locale = practitioners.preferencesOf(practitionerId).get("locale");
    return "en".equals(locale) ? Locale.ENGLISH : Locale.FRENCH;
  }

  static String truncate(String s) {
    return s.length() <= MAX_RELAY_ERROR ? s : s.substring(0, MAX_RELAY_ERROR);
  }

  /** Purge physique des demandes dont purge_after est dépassé ; retourne le nombre supprimé. */
  int purgeDue() {
    return repository.deleteDue();
  }

  @PreDestroy
  void stop() {
    relay.shutdown();
  }
}
