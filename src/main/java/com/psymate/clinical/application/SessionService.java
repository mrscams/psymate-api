package com.psymate.clinical.application;

import com.psymate.clinical.api.SessionView;
import com.psymate.clinical.domain.ClinicalError;
import com.psymate.clinical.domain.ClinicalException;
import com.psymate.clinical.domain.Entry;
import com.psymate.clinical.domain.EntryContent;
import com.psymate.clinical.domain.EntryStatus;
import com.psymate.clinical.domain.EntryType;
import com.psymate.clinical.domain.SessionDetail;
import com.psymate.clinical.domain.TimeSpan;
import com.psymate.clinical.domain.TimeSpec;
import com.psymate.clinical.domain.Visibility;
import com.psymate.clinical.persistence.EntryRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.patients.api.PatientOwnershipGuard;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie des séances (UC-04, F11) : démarrer, clôturer, consigner un contact hors rendez-vous
 * et trouver la séance courante. Une séance est une entrée de type session ; toute écriture passe
 * par {@link EntryService} (versions, événements, patient.clinical_rev).
 */
@Service
@Transactional
public class SessionService {

  /** Durée de repli si le praticien n'a pas de préférence sessions.defaultDurationMin. */
  static final int FALLBACK_DURATION_MIN = 60;

  private static final String DEFAULT_DURATION_KEY = "sessions.defaultDurationMin";
  private static final Set<String> CONTACT_MODALITIES = Set.of("phone", "email");
  private static final String CLOSING_REASON = "Clôture de la séance";

  private final EntryRepository repo;
  private final EntryService entries;
  private final EntryValidators validators;
  private final PatientOwnershipGuard guard;
  private final PractitionerFacade practitioners;
  private final Clock clock;

  SessionService(
      EntryRepository repo,
      EntryService entries,
      EntryValidators validators,
      PatientOwnershipGuard guard,
      PractitionerFacade practitioners,
      Clock clock) {
    this.repo = repo;
    this.entries = entries;
    this.validators = validators;
    this.guard = guard;
    this.practitioners = practitioners;
    this.clock = clock;
  }

  /**
   * Démarre une séance (ouverte, durée inconnue). La séance d'accueil, ouverte par construction, ne
   * compte pas comme séance ouverte.
   *
   * @throws ClinicalException SESSION_ALREADY_OPEN (409) si une séance est déjà ouverte
   */
  public UUID start(UUID patientId, StartSessionCommand cmd) {
    CurrentPractitioner me = currentPractitioner();
    guard.requireOwned(patientId);
    repo.lockSessions(patientId);
    if (repo.openSession(patientId).isPresent()) {
      throw new ClinicalException(
          ClinicalError.SESSION_ALREADY_OPEN, "Une séance est déjà ouverte pour ce patient");
    }
    Instant now = clock.instant();
    Instant from = cmd.occurredFrom() == null ? now : cmd.occurredFrom();
    EntryContent content =
        sessionContent(
            null,
            new TimeSpan(from, null, TimeSpan.Precision.EXACT, null),
            new SessionDetail(SessionDetail.KIND_SESSION, cmd.modality(), null, null));
    validators.validate(EntryType.SESSION, content);
    return entries.write(me.tenantId(), me.id(), patientId, EntryType.SESSION, null, content, now);
  }

  /**
   * Clôture une séance : durée (sinon préférence du praticien, sinon 60 min), fin de séance et
   * compte rendu sont figés dans une VRAIE version, même dans la fenêtre de grâce. Renvoie l'id de
   * cette version, qui devient le compte rendu de la séance.
   *
   * @throws ClinicalException ENTRY_NOT_FOUND, SESSION_INVALID (pas une séance), ENTRY_ARCHIVED,
   *     SESSION_ALREADY_CLOSED
   */
  public UUID close(UUID sessionId, CloseSessionCommand cmd) {
    CurrentPractitioner me = currentPractitioner();
    Entry entry =
        repo.findForUpdate(sessionId)
            .orElseThrow(
                () -> new ClinicalException(ClinicalError.ENTRY_NOT_FOUND, "Séance introuvable"));
    guard.requireOwned(entry.patientId());
    if (entry.type() != EntryType.SESSION) {
      throw new ClinicalException(ClinicalError.SESSION_INVALID, "L'entrée n'est pas une séance");
    }
    if (entry.archived()) {
      throw new ClinicalException(ClinicalError.ENTRY_ARCHIVED, "Séance archivée");
    }
    SessionDetail detail = repo.session(sessionId).orElseThrow();
    if (detail.closedAt() != null) {
      throw new ClinicalException(
          ClinicalError.SESSION_ALREADY_CLOSED, "La séance est déjà clôturée");
    }

    int duration = cmd.durationMin() != null ? cmd.durationMin() : defaultDuration(me);
    Instant closedAt = clock.instant();
    Instant from = entry.time().from();
    String summary =
        cmd.summaryBody() == null || cmd.summaryBody().isBlank() ? null : cmd.summaryBody();
    entries.correct(
        sessionId,
        new CorrectEntryCommand(
            null,
            summary,
            null,
            null,
            new TimeSpec(
                TimeSpan.Precision.EXACT,
                from,
                from.plus(duration, ChronoUnit.MINUTES),
                null,
                false),
            null,
            null,
            new SessionDetail(detail.kind(), detail.modality(), duration, closedAt),
            CLOSING_REASON),
        true);
    UUID versionId = repo.currentVersion(sessionId).orElseThrow().id();
    repo.setSummaryVersion(sessionId, versionId);
    return versionId;
  }

  /**
   * Séance courante du patient : l'ouverte, sinon la dernière clôturée (séance ou contact), sinon
   * la séance d'accueil, sinon rien (l'accueil est créé de façon asynchrone et peut manquer).
   */
  @Transactional(readOnly = true)
  public Optional<SessionView> current(UUID patientId) {
    currentPractitioner();
    guard.requireOwned(patientId);
    return repo.currentSession(patientId);
  }

  /**
   * Consigne un contact hors rendez-vous (téléphone, e-mail) : séance de kind contact, clôturée
   * d'emblée, dont le texte est le compte rendu. Ne touche pas à la séance ouverte.
   *
   * @throws ClinicalException SESSION_INVALID (modalité autre que phone/email, texte vide),
   *     INVALID_TIME (date absente)
   */
  public UUID contact(UUID patientId, ContactCommand cmd) {
    CurrentPractitioner me = currentPractitioner();
    guard.requireOwned(patientId);
    if (cmd.modality() == null || !CONTACT_MODALITIES.contains(cmd.modality())) {
      throw new ClinicalException(
          ClinicalError.SESSION_INVALID, "Un contact se fait par téléphone (phone) ou e-mail");
    }
    if (cmd.occurredFrom() == null) {
      throw new ClinicalException(ClinicalError.INVALID_TIME, "La date du contact est obligatoire");
    }
    if (cmd.body() == null || cmd.body().isBlank()) {
      throw new ClinicalException(ClinicalError.SESSION_INVALID, "Le texte du contact est vide");
    }
    Instant now = clock.instant();
    Instant to =
        cmd.durationMin() == null
            ? cmd.occurredFrom()
            : cmd.occurredFrom().plus(cmd.durationMin(), ChronoUnit.MINUTES);
    EntryContent content =
        sessionContent(
            cmd.body(),
            new TimeSpan(cmd.occurredFrom(), to, TimeSpan.Precision.EXACT, null),
            new SessionDetail(SessionDetail.KIND_CONTACT, cmd.modality(), cmd.durationMin(), now));
    validators.validate(EntryType.SESSION, content);
    UUID id =
        entries.write(me.tenantId(), me.id(), patientId, EntryType.SESSION, null, content, now);
    repo.setSummaryVersion(id, repo.currentVersion(id).orElseThrow().id());
    return id;
  }

  // ---- internes

  private CurrentPractitioner currentPractitioner() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new ClinicalException(ClinicalError.UNAUTHENTICATED, "Non authentifié"));
  }

  private int defaultDuration(CurrentPractitioner me) {
    Object value = practitioners.preferencesOf(me.id()).get(DEFAULT_DURATION_KEY);
    return value instanceof Number n ? n.intValue() : FALLBACK_DURATION_MIN;
  }

  private static EntryContent sessionContent(String body, TimeSpan time, SessionDetail session) {
    return new EntryContent(
        null, body, Visibility.CLINICAL, EntryStatus.FINAL, time, null, null, session);
  }
}
