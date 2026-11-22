package com.psymate.agenda.service;

import com.psymate.agenda.api.AppointmentCancelled;
import com.psymate.agenda.api.AppointmentConfirmed;
import com.psymate.agenda.api.AppointmentEvent;
import com.psymate.agenda.api.AppointmentPatientAttached;
import com.psymate.agenda.api.AppointmentPlanned;
import com.psymate.agenda.api.AppointmentRescheduled;
import com.psymate.agenda.dto.AppointmentAssembler;
import com.psymate.agenda.dto.AppointmentCreatedDto;
import com.psymate.agenda.dto.AppointmentDto;
import com.psymate.agenda.dto.AppointmentSeriesDto;
import com.psymate.agenda.dto.AppointmentUpdatedDto;
import com.psymate.agenda.dto.AttachedAppointmentsDto;
import com.psymate.agenda.dto.ConflictDto;
import com.psymate.agenda.dto.CreateAppointmentRequest;
import com.psymate.agenda.dto.GuestRequest;
import com.psymate.agenda.dto.RecurrenceRequest;
import com.psymate.agenda.dto.UpdateAppointmentRequest;
import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentException;
import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.AppointmentOrigin;
import com.psymate.agenda.model.AppointmentStatus;
import com.psymate.agenda.model.BookingRequest;
import com.psymate.agenda.repository.AppointmentRepository;
import com.psymate.agenda.repository.BookingRequestRepository;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientAccessException;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.patients.api.PatientRef;
import com.psymate.shared.problem.ProblemException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie d'un rendez-vous (M11-05) : créer, lire, déplacer, annuler, confirmer ; séries
 * (M11-07) : créer toutes les occurrences d'une répétition, annuler une série. Déplacer ou annuler
 * une occurrence ne touche jamais aux autres (G Q55).
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
  private final BookingRequestRepository bookingRequests;
  private final AppointmentAssembler assembler;
  private final PatientOwnershipGuard guard;
  private final DomainEventPublisher events;
  private final Clock clock;

  public AppointmentService(
      AppointmentRepository appointments,
      BookingRequestRepository bookingRequests,
      AppointmentAssembler assembler,
      PatientOwnershipGuard guard,
      DomainEventPublisher events,
      Clock clock) {
    this.appointments = appointments;
    this.bookingRequests = bookingRequests;
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

  /**
   * Crée un rendez-vous, ou toutes les occurrences d'une série dans la même transaction (G Q55 :
   * même {@code series_id}, même statut, même note, à heure locale constante dans {@code zone}).
   * Les chevauchements de chaque occurrence sont signalés, jamais bloquants (G Q54).
   */
  @Transactional
  public AppointmentCreatedDto create(CreateAppointmentRequest request, ZoneId zone) {
    CurrentPractitioner me = me();
    // Une seule lecture des patients du praticien par requête : archivé, DTO et conflits.
    Map<UUID, PatientRef> refs;
    Appointment.Guest guest = null;
    if (request.guest() != null) {
      // Invité (G Q60) : aucune fiche patient, donc ni garde ni contrôle d'archivage.
      guest = guestOf(request.guest());
      refs = assembler.patientsOf(me.id());
    } else {
      refs = requireAttachable(request.patientId(), me);
    }
    RecurrenceRequest recurrence = request.recurrence();
    Instant first = utc(request.startsAt()).toInstant();
    // Toutes les occurrences sont calculées avant toute écriture : au-delà de 26, rien n'est créé.
    List<Instant> starts =
        recurrence == null
            ? List.of(first)
            : RecurrenceCalculator.occurrences(
                first, zone, recurrence.everyWeeks(), recurrence.count(), recurrence.until());
    UUID seriesId = recurrence == null ? null : UUID.randomUUID();
    OffsetDateTime createdAt = now();
    List<Appointment> created = new ArrayList<>();
    List<ConflictDto> conflicts = new ArrayList<>();
    for (Instant start : starts) {
      OffsetDateTime startsAt = utc(start);
      Appointment appointment =
          guest == null
              ? new Appointment(
                  UUID.randomUUID(),
                  me.tenantId(),
                  me.id(),
                  request.patientId(),
                  seriesId,
                  startsAt,
                  endsAt(startsAt, request.durationMin()),
                  AppointmentModality.valueOf(request.modality()),
                  Boolean.TRUE.equals(request.tentative()),
                  request.planningNote(),
                  createdAt)
              : Appointment.forGuest(
                  UUID.randomUUID(),
                  me.tenantId(),
                  me.id(),
                  guest,
                  seriesId,
                  startsAt,
                  endsAt(startsAt, request.durationMin()),
                  AppointmentModality.valueOf(request.modality()),
                  Boolean.TRUE.equals(request.tentative()),
                  request.planningNote(),
                  createdAt);
      // Lu avant toute insertion : les occurrences d'une série, à une semaine au moins d'écart et
      // de 600 minutes au plus, ne se chevauchent jamais entre elles.
      conflicts.addAll(assembler.conflicts(overlapsOf(appointment), refs, start));
      created.add(appointment);
    }
    appointments.saveAll(created);
    created.forEach(a -> events.publish(event(AppointmentPlanned::new, a)));
    return new AppointmentCreatedDto(
        created.stream().map(a -> assembler.toDto(a, refs)).toList(), conflicts);
  }

  /**
   * Annule une série (G Q53, Q55) : chaque occurrence encore active ({@code planned}, {@code
   * tentative}) passe à {@code cancelled} avec le même motif, déplacée ou non ; les occurrences
   * annulées ou terminées restent telles quelles. 404 SERIES_NOT_FOUND si aucune occurrence n'est
   * au praticien. Un {@code AppointmentCancelled} par occurrence annulée. Rien n'est supprimé.
   */
  @Transactional
  public AppointmentSeriesDto cancelSeries(UUID seriesId, String reason) {
    CurrentPractitioner me = me();
    List<Appointment> series =
        appointments.findBySeriesIdAndPractitionerIdOrderByStartsAtAscIdAsc(seriesId, me.id());
    if (series.isEmpty()) {
      throw AppointmentException.seriesNotFound();
    }
    OffsetDateTime at = now();
    for (Appointment appointment : series) {
      if (appointment.getStatus().isActive()) {
        appointment.cancel(reason, at);
        events.publish(event(AppointmentCancelled::new, appointment));
      }
    }
    return new AppointmentSeriesDto(assembler.toDtos(series));
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
    if (request.getGuest() != null) {
      if (!appointment.isGuest() || appointment.isGuestPurged()) {
        throw AppointmentException.guestNotEditable();
      }
      Appointment.Guest guest = merged(currentGuest(appointment), request.getGuest());
      if (!guest.equals(currentGuest(appointment))) {
        appointment.correctGuest(guest, at);
      }
    }
    if (rescheduled) {
      events.publish(event(AppointmentRescheduled::new, appointment));
    }
    Map<UUID, PatientRef> refs = assembler.patientsOf(appointment.getPractitionerId());
    return new AppointmentUpdatedDto(
        assembler.toDto(appointment, refs),
        assembler.conflicts(overlapsOf(appointment), refs, appointment.getStartsAt().toInstant()));
  }

  /**
   * Annule : idempotent (un second appel renvoie le rendez-vous inchangé, sans événement), 409 si
   * {@code done}. Aucune suppression (G Q53). Un rendez-vous pris en ligne clôt sa demande ({@code
   * booking_request.closed_by = practitioner}, M14-06).
   */
  @Transactional
  public AppointmentDto cancel(UUID id, String reason) {
    Appointment appointment = owned(id, me());
    boolean wasCancelled = appointment.getStatus() == AppointmentStatus.cancelled;
    appointment.cancel(reason, now());
    if (!wasCancelled) {
      if (appointment.getOrigin() == AppointmentOrigin.ONLINE) {
        // Demande en ligne refusée ou annulée par le praticien (K3.3, M14-06).
        bookingRequests
            .findByAppointmentId(appointment.getId())
            .ifPresent(r -> r.close(BookingRequest.CLOSED_BY_PRACTITIONER));
      }
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

  /**
   * Rattache un rendez-vous invité à un patient du praticien (G Q60) : garde de propriété (403 /
   * 404 des patients), patient non archivé (422 PATIENT_ARCHIVED), 409 APPOINTMENT_NOT_ACTIVE si le
   * rendez-vous vise déjà un patient ou si l'invité est purgé. Avec {@code includeSameGuest}, les
   * autres rendez-vous invités non purgés du praticien de même identité ({@link #sameGuest}) sont
   * rattachés aussi. Un {@code AppointmentPatientAttached} par rendez-vous rattaché.
   */
  @Transactional
  public AttachedAppointmentsDto attachPatient(UUID id, UUID patientId, boolean includeSameGuest) {
    CurrentPractitioner me = me();
    Appointment appointment = owned(id, me);
    if (!appointment.isGuest() || appointment.isGuestPurged()) {
      throw AppointmentException.notActive();
    }
    Map<UUID, PatientRef> refs = requireAttachable(patientId, me);
    List<Appointment> attached = new ArrayList<>();
    if (includeSameGuest) {
      for (Appointment other :
          appointments
              .findByPractitionerIdAndPatientIdIsNullAndGuestPurgedAtIsNullOrderByStartsAtAscIdAsc(
                  me.id())) {
        if (other.equals(appointment) || sameGuest(other, appointment)) {
          attached.add(other);
        }
      }
    } else {
      attached.add(appointment);
    }
    OffsetDateTime at = now();
    for (Appointment a : attached) {
      a.attachPatient(patientId, at);
      events.publish(event(AppointmentPatientAttached::new, a));
    }
    return new AttachedAppointmentsDto(
        attached.stream().map(a -> assembler.toDto(a, refs)).toList());
  }

  /**
   * Patient du praticien (garde, 403 / 404 des patients) et non archivé (422 PATIENT_ARCHIVED) ;
   * renvoie la liste des patients du praticien, lue une fois pour la requête.
   */
  private Map<UUID, PatientRef> requireAttachable(UUID patientId, CurrentPractitioner me) {
    guard.requireOwned(patientId);
    Map<UUID, PatientRef> refs = assembler.patientsOf(me.id());
    PatientRef patient = refs.get(patientId);
    if (patient == null) {
      // Impossible après la garde, sauf course avec un transfert : on échoue franchement.
      throw PatientAccessException.notFound();
    }
    if (patient.archived()) {
      throw AppointmentException.patientArchived();
    }
    return refs;
  }

  // ---- invités (G Q60)

  /**
   * Identité reçue, espaces de bord retirés ; prénom, téléphone ou e-mail vide → null. Une série
   * recopie cette identité, e-mail compris, sur chaque occurrence.
   */
  static Appointment.Guest guestOf(GuestRequest request) {
    return new Appointment.Guest(
        blankToNull(request.firstName()),
        request.lastName().strip(),
        blankToNull(request.phone()),
        blankToNull(request.email()));
  }

  /**
   * Correction (PATCH) : nom remplacé ; prénom, téléphone et e-mail absents (null) gardés, vides
   * effacés.
   */
  static Appointment.Guest merged(Appointment.Guest current, GuestRequest request) {
    return new Appointment.Guest(
        request.firstName() == null ? current.firstName() : blankToNull(request.firstName()),
        request.lastName().strip(),
        request.phone() == null ? current.phone() : blankToNull(request.phone()),
        request.email() == null ? current.email() : blankToNull(request.email()));
  }

  private static Appointment.Guest currentGuest(Appointment a) {
    return new Appointment.Guest(
        a.getGuestFirstName(), a.getGuestLastName(), a.getGuestPhone(), a.getGuestEmail());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  /**
   * Même invité : nom, prénom et téléphone égaux sans tenir compte de la casse ni des espaces («
   * Sara Benali » = « sara benali » ; « +33 6 12 34 56 78 » = « +33612345678 »).
   */
  static boolean sameGuest(Appointment a, Appointment b) {
    return normalized(a.getGuestFirstName()).equals(normalized(b.getGuestFirstName()))
        && normalized(a.getGuestLastName()).equals(normalized(b.getGuestLastName()))
        && normalized(a.getGuestPhone()).equals(normalized(b.getGuestPhone()));
  }

  /** Minuscules, sans aucun espace ; null → chaîne vide. */
  static String normalized(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(value.length());
    value
        .codePoints()
        .filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c))
        .forEach(sb::appendCodePoint);
    return sb.toString().toLowerCase(Locale.ROOT);
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
