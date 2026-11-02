package com.psymate.clinical.service;

import com.psymate.clinical.api.CreatedLink;
import com.psymate.clinical.api.EntryArchived;
import com.psymate.clinical.api.EntryCorrected;
import com.psymate.clinical.api.EntryCreated;
import com.psymate.clinical.api.EntryLinksPort;
import com.psymate.clinical.api.LinkRequest;
import com.psymate.clinical.api.SessionView;
import com.psymate.clinical.model.ClinicalError;
import com.psymate.clinical.model.ClinicalException;
import com.psymate.clinical.model.Entry;
import com.psymate.clinical.model.EntryContent;
import com.psymate.clinical.model.EntryStatus;
import com.psymate.clinical.model.EntryType;
import com.psymate.clinical.model.EntryVersion;
import com.psymate.clinical.model.FeelingDetail;
import com.psymate.clinical.model.GraceRule;
import com.psymate.clinical.model.MedicationDetail;
import com.psymate.clinical.model.SessionDetail;
import com.psymate.clinical.model.TimeSpan;
import com.psymate.clinical.model.TimeSpec;
import com.psymate.clinical.model.Visibility;
import com.psymate.clinical.repository.EntryRepository;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientFacade;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.vocabulary.api.EmotionAffect;
import com.psymate.vocabulary.api.EmotionCatalog;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cas d'usage d'écriture des entrées : création, correction (version ou grâce), archivage. Toute
 * écriture incrémente patient.clinical_rev (G Q30) et publie un événement de domaine, sauf la
 * modification en période de grâce (A4.8), qui n'émet rien.
 */
@Service
public class EntryService {

  private final EntryRepository repo;
  private final EntryValidators validators;
  private final PatientOwnershipGuard guard;
  private final PatientFacade patients;
  private final EmotionCatalog emotions;
  private final DomainEventPublisher events;
  private final ObjectProvider<EntryLinksPort> linksPort;
  private final JsonMapper json;
  private final Clock clock;
  private final GraceRule graceRule;

  EntryService(
      EntryRepository repo,
      EntryValidators validators,
      PatientOwnershipGuard guard,
      PatientFacade patients,
      EmotionCatalog emotions,
      DomainEventPublisher events,
      ObjectProvider<EntryLinksPort> linksPort,
      JsonMapper json,
      Clock clock,
      @Value("${versioning.grace-period:75m}") Duration gracePeriod) {
    this.repo = repo;
    this.validators = validators;
    this.guard = guard;
    this.patients = patients;
    this.emotions = emotions;
    this.events = events;
    this.linksPort = linksPort;
    this.json = json;
    this.clock = clock;
    this.graceRule = new GraceRule(gracePeriod);
  }

  // ---- création

  /** Crée une entrée du praticien courant pour un patient qui lui appartient ; renvoie son id. */
  @Transactional
  public UUID create(UUID patientId, CreateEntryCommand command) {
    return createWithLinks(patientId, command, List.of()).id();
  }

  /**
   * Crée une entrée et ses liens en une seule transaction (M2-02, A7 séquence 2) : écriture de
   * l'entrée (version, détail, tags, clinical_rev), puis un lien manuel accepté par élément de
   * {@code links} par le port de linking, puis EntryCreated. Un lien refusé (exception du port)
   * annule tout : ni entrée, ni lien, ni événement.
   *
   * @throws IllegalStateException des liens sont demandés mais aucun module de liens n'est présent
   */
  @Transactional
  public EntryCreation createWithLinks(
      UUID patientId, CreateEntryCommand command, List<LinkRequest> links) {
    CurrentPractitioner me = currentPractitioner();
    guard.requireOwned(patientId);
    UUID sessionId = checkSession(command.type(), patientId, command.sessionId());

    TimeSpan time = resolve(command.time(), patientId);
    FeelingDetail feeling = command.type() == EntryType.FEELING ? command.feeling() : null;
    MedicationDetail medication =
        command.type() == EntryType.MEDICATION ? command.medication() : null;
    SessionDetail session = command.type() == EntryType.SESSION ? command.session() : null;
    EntryContent content =
        new EntryContent(
            command.title(),
            command.body(),
            command.visibility() == null ? Visibility.CLINICAL : command.visibility(),
            command.status() == null ? EntryStatus.FINAL : command.status(),
            time,
            feeling,
            medication,
            session);
    validators.validate(command.type(), content);

    Instant now = clock.instant();
    if (feeling != null) {
      content = withFeeling(content, copyAffect(feeling));
    }
    if (time == null) {
      content = withTime(content, new TimeSpan(now, now, TimeSpan.Precision.EXACT, null));
    }
    UUID id = insert(me.tenantId(), me.id(), patientId, command.type(), sessionId, content, now);
    writeTags(id, command.tagIds());
    List<CreatedLink> created = createLinks(id, links);
    publishCreated(me.tenantId(), id, patientId, command.type());
    return new EntryCreation(id, created);
  }

  /**
   * Séance d'accueil créée à la création du patient (ADR-10). Idempotent : une seule séance
   * d'accueil par patient, et rien si le patient a déjà la sienne ou n'existe plus.
   */
  @Transactional
  public void createIntakeSession(UUID tenantId, UUID patientId, UUID ownerId, Instant at) {
    repo.lockIntake(patientId);
    if (repo.intakeExists(patientId) || !patients.exists(patientId)) {
      return;
    }
    TimeSpan time = new TimeSpan(at, null, TimeSpan.Precision.EXACT, null);
    SessionDetail session = new SessionDetail(SessionDetail.KIND_INTAKE, "in_person", null, null);
    EntryContent content =
        new EntryContent(
            null, null, Visibility.CLINICAL, EntryStatus.FINAL, time, null, null, session);
    validators.validate(EntryType.SESSION, content);
    write(tenantId, ownerId, patientId, EntryType.SESSION, null, content, clock.instant());
  }

  /**
   * Écriture commune (entrée, version 1, détail, clinical_rev, EntryCreated), partagée avec
   * SessionService.
   */
  UUID write(
      UUID tenantId,
      UUID authorId,
      UUID patientId,
      EntryType type,
      UUID sessionId,
      EntryContent content,
      Instant now) {
    UUID id = insert(tenantId, authorId, patientId, type, sessionId, content, now);
    publishCreated(tenantId, id, patientId, type);
    return id;
  }

  /** Insertion sans événement : entrée, version 1, détail et clinical_rev. */
  private UUID insert(
      UUID tenantId,
      UUID authorId,
      UUID patientId,
      EntryType type,
      UUID sessionId,
      EntryContent content,
      Instant now) {
    UUID id = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    // La FK entry.current_version_id est différée : l'entrée peut précéder sa version.
    repo.insertEntry(
        id,
        tenantId,
        patientId,
        type,
        content.status(),
        content.visibility(),
        content.time(),
        sessionId,
        versionId,
        authorId,
        now);
    repo.insertVersion(
        versionId, id, 1, content.title(), content.body(), payload(content), null, authorId, now);
    insertDetail(id, type, content);
    patients.touchClinicalRev(patientId);
    return id;
  }

  private void publishCreated(UUID tenantId, UUID id, UUID patientId, EntryType type) {
    events.publish(new EntryCreated(tenantId, id, patientId, type.code(), 1));
  }

  /** Liens de l'entrée neuve par le port de linking ; sans effet si la liste est vide. */
  private List<CreatedLink> createLinks(UUID entryId, List<LinkRequest> links) {
    if (links == null || links.isEmpty()) {
      return List.of();
    }
    EntryLinksPort port = linksPort.getIfAvailable();
    if (port == null) {
      throw new IllegalStateException(
          "Des liens sont demandés à la création mais aucune implémentation de EntryLinksPort n'est disponible");
    }
    return port.createForNewEntry(entryId, links);
  }

  // ---- correction

  /**
   * Corrige une entrée : nouvelle version, sauf dans la fenêtre de grâce (même auteur, moins de
   * versioning.grace-period depuis la création, aucun lien, aucune autre version) où l'entrée est
   * modifiée en place, sans version et sans événement (A4.8).
   */
  @Transactional
  public CorrectionResult correct(UUID entryId, CorrectEntryCommand command) {
    return correct(entryId, command, false);
  }

  /**
   * Correction avec choix de la grâce : {@code alwaysVersion} vrai saute la règle de grâce et crée
   * toujours une version (clôture de séance, M1-04).
   */
  @Transactional
  CorrectionResult correct(UUID entryId, CorrectEntryCommand command, boolean alwaysVersion) {
    CurrentPractitioner me = currentPractitioner();
    Entry entry = lockOwned(entryId);
    if (entry.archived()) {
      throw new ClinicalException(ClinicalError.ENTRY_ARCHIVED, "Entrée archivée");
    }
    EntryVersion currentVersion = repo.currentVersion(entryId).orElseThrow();
    EntryContent current = currentContent(entry);
    EntryContent merged = merge(entry, current, command);
    validators.validate(entry.type(), merged);
    if (merged.feeling() != null && merged.feeling() != current.feeling()) {
      merged = withFeeling(merged, correctedAffect(current.feeling(), merged.feeling()));
    }
    boolean tagsChanged = replaceTagsIfChanged(entryId, command.tagIds());
    if (merged.equals(current)) {
      if (tagsChanged) {
        patients.touchClinicalRev(entry.patientId());
      }
      return new CorrectionResult(entryId, entry.currentVersion(), tagsChanged, false);
    }

    Instant now = clock.instant();
    int versionCount = repo.versionCount(entryId);
    if (!alwaysVersion && graceRule.allows(entry, me.id(), versionCount, now)) {
      repo.updateEntryInPlace(entryId, merged.status(), merged.visibility(), merged.time(), now);
      repo.updateVersionInPlace(
          currentVersion.id(), merged.title(), merged.body(), payload(merged));
      updateDetail(entryId, entry.type(), merged);
      patients.touchClinicalRev(entry.patientId());
      return new CorrectionResult(entryId, entry.currentVersion(), true, false);
    }

    int next = entry.currentVersion() + 1;
    UUID versionId = UUID.randomUUID();
    repo.insertVersion(
        versionId,
        entryId,
        next,
        merged.title(),
        merged.body(),
        payload(merged),
        command.reason(),
        me.id(),
        now);
    repo.updateEntryContent(
        entryId, merged.status(), merged.visibility(), merged.time(), versionId, next, now);
    updateDetail(entryId, entry.type(), merged);
    patients.touchClinicalRev(entry.patientId());
    events.publish(
        new EntryCorrected(
            entry.tenantId(),
            entryId,
            entry.patientId(),
            entry.type().code(),
            entry.currentVersion(),
            next));
    return new CorrectionResult(entryId, next, true, true);
  }

  // ---- archivage

  /** Archive l'entrée (suppression logique, jamais physique). Sans effet si déjà archivée. */
  @Transactional
  public void archive(UUID entryId) {
    currentPractitioner();
    Entry entry = lockOwned(entryId);
    if (entry.archived()) {
      return;
    }
    repo.archive(entryId, clock.instant());
    patients.touchClinicalRev(entry.patientId());
    events.publish(
        new EntryArchived(
            entry.tenantId(),
            entryId,
            entry.patientId(),
            entry.type().code(),
            entry.currentVersion()));
  }

  // ---- restauration

  /**
   * Désarchive l'entrée (A7 : archived → final). Sans effet si elle n'est pas archivée. Aucun
   * événement : A4.8 n'en définit pas pour la restauration ; patient.clinical_rev est incrémenté.
   */
  @Transactional
  public void restore(UUID entryId) {
    currentPractitioner();
    Entry entry = lockOwned(entryId);
    if (!entry.archived()) {
      return;
    }
    repo.unarchive(entryId, clock.instant());
    patients.touchClinicalRev(entry.patientId());
  }

  // ---- internes

  /** Pose les tags (déjà résolus) d'une entrée neuve ; sans effet si la liste est vide. */
  private void writeTags(UUID entryId, List<UUID> tagIds) {
    if (tagIds != null && !tagIds.isEmpty()) {
      repo.replaceTags(entryId, List.copyOf(new LinkedHashSet<>(tagIds)));
    }
  }

  /**
   * Remplace les tags de l'entrée s'ils diffèrent ; nul : inchangés. Les tags ne sont pas dans
   * entry_version : ce changement ne crée jamais de version.
   */
  private boolean replaceTagsIfChanged(UUID entryId, List<UUID> wanted) {
    if (wanted == null) {
      return false;
    }
    Set<UUID> target = new LinkedHashSet<>(wanted);
    if (new HashSet<>(repo.tagIds(entryId)).equals(target)) {
      return false;
    }
    repo.replaceTags(entryId, List.copyOf(target));
    return true;
  }

  private CurrentPractitioner currentPractitioner() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new ClinicalException(ClinicalError.UNAUTHENTICATED, "Non authentifié"));
  }

  private Entry lockOwned(UUID entryId) {
    Entry entry =
        repo.findForUpdate(entryId)
            .orElseThrow(
                () -> new ClinicalException(ClinicalError.ENTRY_NOT_FOUND, "Entrée introuvable"));
    guard.requireOwned(entry.patientId());
    return entry;
  }

  /**
   * ADR-10 : session_id obligatoire sauf pour une séance ; la séance est du même patient. Sans
   * sessionId, la séance courante du patient est utilisée.
   */
  private UUID checkSession(EntryType type, UUID patientId, UUID sessionId) {
    if (type == null) {
      throw new ClinicalException(ClinicalError.SESSION_INVALID, "Type d'entrée obligatoire");
    }
    if (type == EntryType.SESSION) {
      if (sessionId != null) {
        throw new ClinicalException(
            ClinicalError.SESSION_NOT_ALLOWED, "Une séance n'est rattachée à aucune séance");
      }
      return null;
    }
    if (sessionId == null) {
      // Séance courante du patient (ouverte, sinon dernière clôturée, sinon accueil) : M1-04.
      return repo.currentSession(patientId)
          .map(SessionView::id)
          .orElseThrow(
              () ->
                  new ClinicalException(
                      ClinicalError.SESSION_REQUIRED, "Toute entrée appartient à une séance"));
    }
    if (!repo.isSessionOfPatient(sessionId, patientId)) {
      throw new ClinicalException(
          ClinicalError.SESSION_INVALID, "sessionId doit désigner une séance de ce patient");
    }
    return sessionId;
  }

  private TimeSpan resolve(TimeSpec spec, UUID patientId) {
    if (spec == null) {
      return null;
    }
    LocalDate birthDate =
        spec.precision() == TimeSpan.Precision.LIFE_PHASE
            ? patients.birthDate(patientId).orElse(null)
            : null;
    return TimeSpan.resolve(spec, birthDate);
  }

  /** Valence et activation copiées de l'émotion si elle les a, NULL sinon (A4.5). */
  private FeelingDetail copyAffect(FeelingDetail feeling) {
    EmotionAffect affect =
        emotions
            .affectOf(feeling.emotionCode())
            .orElseThrow(
                () ->
                    new ClinicalException(
                        ClinicalError.UNKNOWN_EMOTION,
                        "Émotion inconnue ou inactive : " + feeling.emotionCode()));
    return feeling.withAffect(affect.valence(), affect.arousal());
  }

  /** Même émotion : on garde ce qui avait été copié à la saisie ; sinon nouvelle copie. */
  private FeelingDetail correctedAffect(FeelingDetail before, FeelingDetail after) {
    if (before != null && before.emotionCode().equals(after.emotionCode())) {
      return after.withAffect(before.valence(), before.arousal());
    }
    return copyAffect(after);
  }

  private EntryContent currentContent(Entry entry) {
    EntryVersion v = repo.currentVersion(entry.id()).orElseThrow();
    return new EntryContent(
        v.title(),
        v.body(),
        entry.visibility(),
        entry.status(),
        entry.time(),
        entry.type() == EntryType.FEELING ? repo.feeling(entry.id()).orElseThrow() : null,
        entry.type() == EntryType.MEDICATION ? repo.medication(entry.id()).orElseThrow() : null,
        entry.type() == EntryType.SESSION ? repo.session(entry.id()).orElseThrow() : null);
  }

  private EntryContent merge(Entry entry, EntryContent cur, CorrectEntryCommand c) {
    EntryType type = entry.type();
    FeelingDetail feeling = cur.feeling();
    if (type == EntryType.FEELING && c.feeling() != null) {
      feeling =
          new FeelingDetail(
              c.feeling().emotionCode(),
              feeling.originalEmotionCode(),
              c.feeling().intensity(),
              null,
              null,
              c.feeling().contextNote(),
              c.feeling().bodilySigns());
    }
    return new EntryContent(
        c.title() != null ? c.title() : cur.title(),
        c.body() != null ? c.body() : cur.body(),
        c.visibility() != null ? c.visibility() : cur.visibility(),
        c.status() != null ? c.status() : cur.status(),
        c.time() != null ? resolve(c.time(), entry.patientId()) : cur.time(),
        feeling,
        type == EntryType.MEDICATION && c.medication() != null ? c.medication() : cur.medication(),
        type == EntryType.SESSION && c.session() != null ? c.session() : cur.session());
  }

  private static EntryContent withFeeling(EntryContent c, FeelingDetail feeling) {
    return new EntryContent(
        c.title(),
        c.body(),
        c.visibility(),
        c.status(),
        c.time(),
        feeling,
        c.medication(),
        c.session());
  }

  private static EntryContent withTime(EntryContent c, TimeSpan time) {
    return new EntryContent(
        c.title(),
        c.body(),
        c.visibility(),
        c.status(),
        time,
        c.feeling(),
        c.medication(),
        c.session());
  }

  private void insertDetail(UUID id, EntryType type, EntryContent c) {
    switch (type) {
      case FEELING -> repo.insertFeeling(id, c.feeling());
      case MEDICATION -> repo.insertMedication(id, c.medication());
      case SESSION -> repo.insertSession(id, c.session());
      default -> {}
    }
  }

  private void updateDetail(UUID id, EntryType type, EntryContent c) {
    switch (type) {
      case FEELING -> repo.updateFeeling(id, c.feeling());
      case MEDICATION -> repo.updateMedication(id, c.medication());
      case SESSION -> repo.updateSession(id, c.session());
      default -> {}
    }
  }

  /**
   * Copie du détail typé et des attributs de l'entrée au moment de la version (E3.6). Le texte
   * libre reste dans title et body, jamais dans le payload.
   */
  private String payload(EntryContent c) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("visibility", c.visibility().code());
    p.put("status", c.status().code());
    Map<String, Object> occurred = new LinkedHashMap<>();
    occurred.put("from", c.time().from().toString());
    occurred.put("to", c.time().to() == null ? null : c.time().to().toString());
    occurred.put("precision", c.time().precision().code());
    occurred.put("label", c.time().label());
    p.put("occurred", occurred);
    if (c.feeling() != null) {
      p.put("detail", c.feeling());
    } else if (c.medication() != null) {
      p.put("detail", c.medication());
    } else if (c.session() != null) {
      p.put("detail", c.session());
    }
    return json.writeValueAsString(p);
  }
}
