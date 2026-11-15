package com.psymate.agenda.service;

import com.psymate.agenda.api.AppointmentCancelled;
import com.psymate.agenda.api.AppointmentConfirmed;
import com.psymate.agenda.api.AppointmentEvent;
import com.psymate.agenda.api.AppointmentPlanned;
import com.psymate.agenda.api.AppointmentRescheduled;
import com.psymate.agenda.dto.AppointmentAssembler;
import com.psymate.agenda.dto.AppointmentCreatedDto;
import com.psymate.agenda.dto.AppointmentDto;
import com.psymate.agenda.dto.AppointmentUpdatedDto;
import com.psymate.agenda.dto.CreateAppointmentRequest;
import com.psymate.agenda.dto.UpdateAppointmentRequest;
import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentException;
import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.AppointmentStatus;
import com.psymate.agenda.repository.AppointmentRepository;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.patients.api.PatientRef;
import com.psymate.shared.problem.ProblemException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie d'un rendez-vous (M11-05) : créer, lire, déplacer, annuler, confirmer.
 *
 * <p>Règles (G Q52 à Q54, Q58, Q59) : le praticien et le cabinet viennent de la session, jamais du
 * corps ; le patient doit être au praticien (garde, 403 / 404) et non archivé (422
 * PATIENT_ARCHIVED) ; le rendez-vous d'un autre praticien est introuvable (404) ; un chevauchement
 * n'est jamais bloquant, il est renvoyé dans {@code conflicts} ; rien n'est jamais supprimé. Chaque
 * transition publie son événement dans la transaction : le registre le persiste au commit. Un
 * rendez-vous n'est pas une écriture clinique : {@code patient.clinical_rev} n'est pas touché.
 */
@Service
public class AppointmentService {

  private final AppointmentRepository appointments;
  private final AppointmentAssembler assembler;
  private final PatientOwnershipGuard guard;
  private final DomainEventPublisher events;
  private final Clock clock;

  public AppointmentService(
      AppointmentRepository appointments,
      AppointmentAssembler assembler,
      PatientOwnershipGuard guard,
      DomainEventPublisher events,
      Clock clock) {
    this.appointments = appointments;
    this.assembler = assembler;
    this.guard = guard;
    this.events = events;
    this.clock = clock;
  }

  // ---- règles pures (testées unitairement)

  /** Fin du créneau : début + durée. */
  static OffsetDateTime endsAt(OffsetDateTime startsAt, int durationMin) {
    return startsAt.plusMinutes(durationMin);
  }

  /**
   * Deux créneaux semi-ouverts {@code [start, end[} se chevauchent : même règle que la requête JPQL
   * {@code findActiveOverlaps}. Un créneau qui finit à 10:00 ne chevauche pas un créneau qui
   * commence à 10:00.
   */
  static boolean overlaps(
      OffsetDateTime aStart, OffsetDateTime aEnd, OffsetDateTime bStart, OffsetDateTime bEnd) {
    return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
  }

  /** Instant reçu en UTC, à la microseconde près (précision de timestamptz). */
  static OffsetDateTime utc(Instant instant) {
    return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new ProblemException(401, "UNAUTHENTICATED", "Session requise."));
  }

  private Appointment owned(UUID id, CurrentPractitioner me) {
    return appointments
        .findByIdAndPractitionerId(id, me.id())
        .orElseThrow(AppointmentException::notFound);
  }

  private List<Appointment> overlapsOf(Appointment a) {
    return appointments.findActiveOverlaps(
        a.getPractitionerId(), a.getStartsAt(), a.getEndsAt(), a.getId());
  }

  // ---- cas d'usage

  /** Crée un rendez-vous ; les chevauchements sont signalés, jamais bloquants (G Q54). */
  @Transactional
  public AppointmentCreatedDto create(CreateAppointmentRequest request) {
    CurrentPractitioner me = me();
    guard.requireOwned(request.patientId());
    PatientRef patient = assembler.patientsOf(me.id()).get(request.patientId());
    if (patient != null && patient.archived()) {
      throw AppointmentException.patientArchived();
    }
    OffsetDateTime startsAt = utc(request.startsAt());
    Appointment appointment =
        new Appointment(
            UUID.randomUUID(),
            me.tenantId(),
            me.id(),
            request.patientId(),
            null,
            startsAt,
            endsAt(startsAt, request.durationMin()),
            AppointmentModality.valueOf(request.modality()),
            Boolean.TRUE.equals(request.tentative()),
            request.planningNote(),
            now());
    List<Appointment> conflicts = overlapsOf(appointment);
    appointments.save(appointment);
    events.publish(event(AppointmentPlanned::new, appointment));
    return new AppointmentCreatedDto(
        List.of(assembler.toDto(appointment)), assembler.conflicts(conflicts));
  }

  /** Détail d'un rendez-vous du praticien courant. */
  @Transactional(readOnly = true)
  public AppointmentDto get(UUID id) {
    return assembler.toDto(owned(id, me()));
  }

  /**
   * Fusion partielle : début (la durée est gardée), durée (le début est gardé), modalité, note
   * ({@code null} explicite l'efface). 409 si le rendez-vous est annulé ou terminé, même pour un
   * corps vide. {@code AppointmentRescheduled} seulement si le créneau change.
   */
  @Transactional
  public AppointmentUpdatedDto update(UUID id, UpdateAppointmentRequest request) {
    Appointment appointment = owned(id, me());
    if (!appointment.getStatus().isActive()) {
      throw AppointmentException.notActive();
    }
    OffsetDateTime at = now();
    OffsetDateTime startsAt =
        request.getStartsAt() == null ? appointment.getStartsAt() : utc(request.getStartsAt());
    int durationMin =
        request.getDurationMin() == null ? appointment.getDurationMin() : request.getDurationMin();
    OffsetDateTime endsAt = endsAt(startsAt, durationMin);
    boolean rescheduled =
        !startsAt.isEqual(appointment.getStartsAt()) || !endsAt.isEqual(appointment.getEndsAt());
    if (rescheduled) {
      appointment.reschedule(startsAt, endsAt, at);
    }
    if (request.getModality() != null) {
      AppointmentModality modality = AppointmentModality.valueOf(request.getModality());
      if (modality != appointment.getModality()) {
        appointment.changeModality(modality, at);
      }
    }
    if (request.planningNoteProvided()
        && !Objects.equals(request.getPlanningNote(), appointment.getPlanningNote())) {
      appointment.changePlanningNote(request.getPlanningNote(), at);
    }
    if (rescheduled) {
      events.publish(event(AppointmentRescheduled::new, appointment));
    }
    return new AppointmentUpdatedDto(
        assembler.toDto(appointment), assembler.conflicts(overlapsOf(appointment)));
  }

  /**
   * Annule : idempotent (un second appel renvoie le rendez-vous inchangé, sans événement), 409 si
   * {@code done}. Aucune suppression (G Q53).
   */
  @Transactional
  public AppointmentDto cancel(UUID id, String reason) {
    Appointment appointment = owned(id, me());
    boolean wasCancelled = appointment.getStatus() == AppointmentStatus.cancelled;
    appointment.cancel(reason, now());
    if (!wasCancelled) {
      events.publish(event(AppointmentCancelled::new, appointment));
    }
    return assembler.toDto(appointment);
  }

  /** Confirme : {@code tentative → planned} ; 409 APPOINTMENT_NOT_ACTIVE pour tout autre statut. */
  @Transactional
  public AppointmentDto confirm(UUID id) {
    Appointment appointment = owned(id, me());
    appointment.confirm(now());
    events.publish(event(AppointmentConfirmed::new, appointment));
    return assembler.toDto(appointment);
  }

  // ---- événements (G Q59)

  /** Constructeur commun des quatre événements (payload G Q59). */
  @FunctionalInterface
  interface EventFactory<E extends AppointmentEvent> {
    E create(
        UUID tenantId,
        UUID appointmentId,
        UUID practitionerId,
        UUID patientId,
        UUID seriesId,
        Instant startsAt,
        Instant endsAt,
        String status);
  }

  /** Événement portant l'état du rendez-vous après la transition. */
  static <E extends AppointmentEvent> E event(EventFactory<E> factory, Appointment a) {
    return factory.create(
        a.getTenantId(),
        a.getId(),
        a.getPractitionerId(),
        a.getPatientId(),
        a.getSeriesId(),
        a.getStartsAt().toInstant(),
        a.getEndsAt().toInstant(),
        a.getStatus().name());
  }
}
