package com.psymate.billing.service;

import com.psymate.billing.dto.BillableSessionAssembler;
import com.psymate.billing.dto.BillableSessionDto;
import com.psymate.billing.dto.BillableSessionsDto;
import com.psymate.billing.dto.DismissedSessionsDto;
import com.psymate.billing.dto.UpdateBillableSessionRequest;
import com.psymate.billing.model.BillableSession;
import com.psymate.billing.model.BillingException;
import com.psymate.billing.model.BillingNumbering;
import com.psymate.billing.model.BillingPatient;
import com.psymate.billing.model.BillingPricing;
import com.psymate.billing.model.BillingService;
import com.psymate.billing.repository.BillableSessionRepository;
import com.psymate.billing.repository.BillingPatientRepository;
import com.psymate.billing.repository.BillingServiceRepository;
import com.psymate.clinical.api.SessionClosed;
import com.psymate.clinical.api.SessionFacade;
import com.psymate.clinical.api.SessionView;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientAccessException;
import com.psymate.patients.api.PatientBillingView;
import com.psymate.patients.api.PatientFacade;
import com.psymate.shared.problem.ProblemException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Séances à facturer (M15-07, G Q86, Q87, L5.2).
 *
 * <p>Une séance de kind {@code session} close crée une ligne {@code pending} : par l'écoute de
 * {@code SessionClosed} ({@link #recordClosed}), ou à la demande quand le praticien agit avant que
 * l'événement soit traité ({@link SessionFacade#closedSession}). Les deux chemins passent par la
 * même insertion idempotente : ni doublon, ni réveil d'une ligne écartée ou facturée. Une séance
 * archivée encore {@code pending} passe à {@code dismissed} ({@link #dismissArchived}). Une séance
 * d'accueil ou de contact, ouverte, archivée, inexistante ou d'un autre praticien n'est pas
 * facturable (404 SESSION_NOT_BILLABLE) ; une séance facturée ne change plus (409
 * SESSION_ALREADY_INVOICED). Un rendez-vous de l'agenda ne crée jamais de ligne.
 *
 * <p>Noms affichés par la façade des patients (vues dont le propriétaire n'est pas le praticien
 * courant écartées) ; aucun texte clinique, aucun nom ni montant journalisé.
 */
@Service
public class BillableSessionService {

  /** Seul kind de séance facturable (G Q86) : ni intake, ni contact. */
  static final String BILLABLE_KIND = "session";

  /** Fenêtre de la liste des séances écartées, en jours (L5.2). */
  static final int DISMISSED_WINDOW_DAYS = 90;

  /** Borne haute quand {@code until} est absent : toute date de PostgreSQL y tient. */
  static final LocalDate NO_UNTIL = LocalDate.of(9999, 12, 31);

  private final BillableSessionRepository sessions;
  private final BillingPatientRepository profiles;
  private final BillingServiceRepository services;
  private final BillableSessionAssembler assembler;
  private final SessionFacade clinicalSessions;
  private final PatientFacade patients;
  private final Clock clock;

  public BillableSessionService(
      BillableSessionRepository sessions,
      BillingPatientRepository profiles,
      BillingServiceRepository services,
      BillableSessionAssembler assembler,
      SessionFacade clinicalSessions,
      PatientFacade patients,
      Clock clock) {
    this.sessions = sessions;
    this.profiles = profiles;
    this.services = services;
    this.assembler = assembler;
    this.clinicalSessions = clinicalSessions;
    this.patients = patients;
    this.clock = clock;
  }

  // ---- règles pures (testées unitairement)

  /** Jour d'une séance : la date de son début au fuseau Europe/Paris. */
  static LocalDate occurredOn(Instant occurredFrom) {
    return occurredFrom.atZone(BillingNumbering.ZONE).toLocalDate();
  }

  /** Une séance close est facturable si elle est de kind session (G Q86). */
  static boolean billableKind(String kind) {
    return BILLABLE_KIND.equals(kind);
  }

  // ---- événements (sans praticien connecté)

  /**
   * Séance close : crée sa ligne {@code pending} si c'est une séance de kind session, toujours
   * close et non archivée dans clinical, et qu'aucune ligne n'existe encore. Idempotent. Le
   * contrôle auprès de clinical couvre l'ordre non garanti des écouteurs asynchrones (et la
   * republication au redémarrage) : si {@code EntryArchived} est traité avant {@code
   * SessionClosed}, la séance archivée ne naît pas {@code pending}.
   */
  @Transactional
  public void recordClosed(SessionClosed event) {
    if (!billableKind(event.getKind()) || event.getOccurredFrom() == null) {
      return;
    }
    if (!stillClosed(event)) {
      return;
    }
    sessions.insertPendingIfAbsent(
        event.getSessionId(),
        event.getTenantId(),
        event.getPractitionerId(),
        event.getPatientId(),
        occurredOn(event.getOccurredFrom()),
        event.getModality(),
        event.getDurationMin());
  }

  /**
   * Séance archivée : sa ligne encore {@code pending} passe à {@code dismissed}, par une mise à
   * jour conditionnelle (ni lecture préalable, ni réécriture des autres colonnes). Idempotent ; une
   * ligne écartée ou facturée n'est pas touchée.
   */
  @Transactional
  public void dismissArchived(UUID sessionEntryId) {
    sessions.dismissIfPending(sessionEntryId, now());
  }

  /**
   * La séance de l'événement est-elle encore close et non archivée ? La façade de clinical ne
   * répond qu'à un praticien connecté (ADR-09) : l'écouteur, hors requête, pose sur son fil le
   * principal du praticien de l'événement le temps de l'appel, puis rétablit le contexte d'avant.
   */
  private boolean stillClosed(SessionClosed event) {
    SecurityContext previous = SecurityContextHolder.getContext();
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    CurrentPractitioner principal =
        new CurrentPractitioner(event.getPractitionerId(), event.getTenantId(), "", "", null, null);
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    SecurityContextHolder.setContext(context);
    try {
      return clinicalSessions
          .closedSession(event.getSessionId())
          .filter(v -> billableKind(v.kind()))
          .isPresent();
    } finally {
      SecurityContextHolder.setContext(previous);
    }
  }

  // ---- lecture

  /**
   * Séances {@code pending} du praticien, jusqu'au {@code until} inclus s'il est donné, d'un seul
   * patient s'il est donné (404 PATIENT_NOT_FOUND pour le patient d'un autre praticien).
   */
  @Transactional(readOnly = true)
  public BillableSessionsDto list(LocalDate until, UUID patientId) {
    CurrentPractitioner me = me();
    LocalDate upTo = until == null ? NO_UNTIL : until;
    LocalDate since = dismissedSince();
    List<BillableSession> pending;
    long dismissedCount;
    if (patientId == null) {
      pending = sessions.findByStateUntil(me.id(), BillableSession.PENDING, upTo);
      dismissedCount =
          sessions.countByPractitionerIdAndStateAndOccurredOnGreaterThanEqual(
              me.id(), BillableSession.DISMISSED, since);
    } else {
      requireOwnPatient(patientId, me);
      pending =
          sessions.findByPatientAndStateUntil(me.id(), patientId, BillableSession.PENDING, upTo);
      dismissedCount =
          sessions.countByPractitionerIdAndPatientIdAndStateAndOccurredOnGreaterThanEqual(
              me.id(), patientId, BillableSession.DISMISSED, since);
    }
    Set<UUID> patientIds = patientIdsOf(pending);
    return assembler.toList(
        pending,
        ownPatients(patientIds, me),
        pricing(pending, patientIds, me),
        sessions.openDraftCounts(me.id(), patientIds),
        sessions.lastInvoices(me.id(), patientIds),
        dismissedCount);
  }

  /** Séances écartées du praticien des 90 derniers jours (au jour de Europe/Paris). */
  @Transactional(readOnly = true)
  public DismissedSessionsDto dismissed() {
    CurrentPractitioner me = me();
    List<BillableSession> dismissed =
        sessions.findByStateSince(me.id(), BillableSession.DISMISSED, dismissedSince());
    Set<UUID> patientIds = patientIdsOf(dismissed);
    return assembler.toDismissed(
        dismissed, ownPatients(patientIds, me), pricing(dismissed, patientIds, me));
  }

  // ---- écriture

  /** « Ne pas facturer » (204, idempotent) ; crée la ligne à la demande (G Q86). */
  @Transactional
  public void dismiss(UUID sessionEntryId) {
    CurrentPractitioner me = me();
    locate(sessionEntryId, me).dismiss(now());
  }

  /**
   * « Remettre à facturer » (204, idempotent) ; crée la ligne à la demande. Une ligne écartée dont
   * la séance a été archivée depuis n'est pas remise (404 SESSION_NOT_BILLABLE) : une séance
   * archivée ne se facture pas.
   */
  @Transactional
  public void restore(UUID sessionEntryId) {
    CurrentPractitioner me = me();
    BillableSession session = locate(sessionEntryId, me);
    if (session.isDismissed() && billableView(sessionEntryId, me).isEmpty()) {
      throw BillingException.sessionNotBillable();
    }
    session.restore(now());
  }

  /**
   * Tarif ou prestation d'une séance (fusion partielle ; null rend le tarif calculé). Une
   * prestation archivée ou d'un autre praticien est refusée (422 serviceId INVALID_VALUE).
   */
  @Transactional
  public BillableSessionDto update(UUID sessionEntryId, UpdateBillableSessionRequest request) {
    CurrentPractitioner me = me();
    BillableSession session = locate(sessionEntryId, me);
    if (session.isInvoiced()) {
      throw BillingException.sessionAlreadyInvoiced();
    }
    if (request.serviceIdProvided() && request.getServiceId() != null) {
      services
          .findByIdAndPractitionerId(request.getServiceId(), me.id())
          .filter(s -> !s.isArchived())
          .orElseThrow(() -> BillingException.invalidField("serviceId", "INVALID_VALUE"));
    }
    OffsetDateTime now = now();
    if (request.amountProvided()) {
      session.changeAmount(request.getAmount(), now);
    }
    if (request.serviceIdProvided()) {
      session.changeService(request.getServiceId(), now);
    }
    BillableSession saved = sessions.saveAndFlush(session);
    return assembler.toDto(saved, pricing(List.of(saved), Set.of(saved.getPatientId()), me));
  }

  // ---- mise en facture (M15-08)

  /**
   * Séance à mettre dans une facture, verrouillée jusqu'à la fin de la transaction : la ligne
   * existante du praticien, ou créée à la demande si la séance est close (G Q86). Une séance
   * écartée dont la séance a été archivée depuis n'est pas facturable (404 SESSION_NOT_BILLABLE),
   * comme pour {@link #restore}. L'état (déjà facturée ailleurs…) est contrôlé par l'appelant.
   * Exige une transaction en cours.
   */
  public BillableSession lockForInvoice(UUID sessionEntryId, CurrentPractitioner me) {
    BillableSession session = locate(sessionEntryId, me);
    if (session.isDismissed() && billableView(sessionEntryId, me).isEmpty()) {
      throw BillingException.sessionNotBillable();
    }
    return session;
  }

  /** Tarifs de ces séances du praticien (G Q87), comme la liste des séances à facturer. */
  public BillingPricing pricingOf(List<BillableSession> rows, CurrentPractitioner me) {
    return pricing(rows, patientIdsOf(rows), me);
  }

  // ---- outils

  /**
   * Ligne de la séance, verrouillée, du praticien courant ; à défaut, créée à la demande si la
   * séance est close, de kind session et au praticien (G Q86), sinon 404 SESSION_NOT_BILLABLE.
   */
  private BillableSession locate(UUID sessionEntryId, CurrentPractitioner me) {
    Optional<BillableSession> existing = sessions.findForUpdate(sessionEntryId, me.id());
    if (existing.isPresent()) {
      return existing.get();
    }
    SessionView view =
        billableView(sessionEntryId, me).orElseThrow(BillingException::sessionNotBillable);
    sessions.insertPendingIfAbsent(
        view.id(),
        me.tenantId(),
        me.id(),
        view.patientId(),
        occurredOn(view.occurredFrom()),
        view.modality(),
        view.durationMin());
    return sessions
        .findForUpdate(sessionEntryId, me.id())
        .orElseThrow(BillingException::sessionNotBillable);
  }

  /** Séance close, de kind session, au praticien courant (façade de clinical). */
  private Optional<SessionView> billableView(UUID sessionEntryId, CurrentPractitioner me) {
    return clinicalSessions
        .closedSession(sessionEntryId)
        .filter(v -> billableKind(v.kind()))
        .filter(v -> me.id().equals(v.practitionerId()))
        .filter(v -> v.occurredFrom() != null);
  }

  /** Le patient doit être au praticien courant : sinon 404 PATIENT_NOT_FOUND. */
  private void requireOwnPatient(UUID patientId, CurrentPractitioner me) {
    patients
        .billingIdentity(patientId)
        .filter(v -> me.id().equals(v.ownerId()))
        .orElseThrow(PatientAccessException::notFound);
  }

  /** Identités de facturation des patients, celles du praticien courant seulement. */
  private Map<UUID, PatientBillingView> ownPatients(Set<UUID> patientIds, CurrentPractitioner me) {
    return patients.billingIdentities(patientIds).entrySet().stream()
        .filter(e -> me.id().equals(e.getValue().ownerId()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /** Tarifs des patients et prestations du praticien utiles à ces séances. */
  private BillingPricing pricing(
      List<BillableSession> rows, Set<UUID> patientIds, CurrentPractitioner me) {
    Map<UUID, Integer> rates = new HashMap<>();
    for (BillingPatient p : profiles.findAllById(patientIds)) {
      if (me.id().equals(p.getPractitionerId()) && p.getRateCents() != null) {
        rates.put(p.getPatientId(), p.getRateCents());
      }
    }
    Map<UUID, BillingService> byId = new HashMap<>();
    BillingService defaultService = null;
    for (BillingService s : services.findActive(me.id())) {
      byId.put(s.getId(), s);
      if (s.isDefault()) {
        defaultService = s;
      }
    }
    Set<UUID> chosen =
        rows.stream()
            .map(BillableSession::getServiceId)
            .filter(Objects::nonNull)
            .filter(id -> !byId.containsKey(id))
            .collect(Collectors.toSet());
    for (BillingService s : services.findAllById(chosen)) {
      if (me.id().equals(s.getPractitionerId())) {
        byId.put(s.getId(), s);
      }
    }
    return new BillingPricing(rates, byId, defaultService);
  }

  private static Set<UUID> patientIdsOf(List<BillableSession> rows) {
    return rows.stream()
        .map(BillableSession::getPatientId)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Premier jour de la fenêtre des séances écartées : aujourd'hui (Europe/Paris) moins 90 jours.
   */
  private LocalDate dismissedSince() {
    return LocalDate.now(clock.withZone(BillingNumbering.ZONE)).minusDays(DISMISSED_WINDOW_DAYS);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new ProblemException(401, "UNAUTHENTICATED", "Session requise."));
  }
}
