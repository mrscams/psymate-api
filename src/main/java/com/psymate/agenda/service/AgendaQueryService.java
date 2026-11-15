package com.psymate.agenda.service;

import com.psymate.agenda.dto.AgendaWindowDto;
import com.psymate.agenda.dto.AppointmentAssembler;
import com.psymate.agenda.dto.AppointmentDefaultsDto;
import com.psymate.agenda.dto.FreeBusyDto;
import com.psymate.agenda.dto.PatientAppointmentsDto;
import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.AppointmentStatus;
import com.psymate.agenda.repository.AppointmentRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.patients.api.FollowUpView;
import com.psymate.patients.api.PatientFacade;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.patients.api.PatientRef;
import com.psymate.shared.problem.ProblemException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectures de l'agenda (M11-06, I3) : fenêtre de la grille, voisinage d'un créneau (freebusy) et
 * rendez-vous d'un patient avec ses défauts de création (G Q56). Lecture seule : aucune écriture,
 * aucune table hors {@code appointment} (les patients et les préférences passent par les façades
 * {@code api}). Toujours bornée au praticien courant (G Q58). Les bornes sont validées par le
 * contrôleur.
 */
@Service
@Transactional(readOnly = true)
public class AgendaQueryService {

  /** Préférence de durée de séance (identity, ADR-11), défaut de repli de G Q56. */
  static final String DEFAULT_DURATION_PREFERENCE = "sessions.defaultDurationMin";

  static final int FALLBACK_DURATION_MIN = 60;

  /** Statuts actifs : ceux qui comptent dans les chevauchements (G Q54). */
  static final Set<AppointmentStatus> ACTIVE =
      EnumSet.of(AppointmentStatus.planned, AppointmentStatus.tentative);

  /** Aucun rendez-vous n'a cet identifiant : « rien à exclure » sans paramètre nul en JPQL. */
  private static final UUID NOTHING = new UUID(0L, 0L);

  private final AppointmentRepository appointments;
  private final AppointmentAssembler assembler;
  private final PatientOwnershipGuard guard;
  private final PatientFacade patients;
  private final PractitionerFacade practitioners;
  private final Clock clock;

  public AgendaQueryService(
      AppointmentRepository appointments,
      AppointmentAssembler assembler,
      PatientOwnershipGuard guard,
      PatientFacade patients,
      PractitionerFacade practitioners,
      Clock clock) {
    this.appointments = appointments;
    this.assembler = assembler;
    this.guard = guard;
    this.patients = patients;
    this.practitioners = practitioners;
    this.clock = clock;
  }

  private static UUID me() {
    return CurrentPractitioner.get()
        .map(CurrentPractitioner::id)
        .orElseThrow(() -> new ProblemException(401, "UNAUTHENTICATED", "Session requise."));
  }

  private static OffsetDateTime utc(Instant at) {
    return at.atOffset(ZoneOffset.UTC);
  }

  /**
   * Rendez-vous du praticien courant qui chevauchent [from, to[, triés par début ; annulés exclus
   * sauf {@code includeCancelled}. {@code busy} est toujours vide (réservé à M12).
   */
  public AgendaWindowDto window(Instant from, Instant to, boolean includeCancelled) {
    UUID practitionerId = me();
    Set<AppointmentStatus> statuses =
        includeCancelled
            ? EnumSet.allOf(AppointmentStatus.class)
            : EnumSet.complementOf(EnumSet.of(AppointmentStatus.cancelled));
    List<Appointment> found = appointments.findWindow(practitionerId, utc(from), utc(to), statuses);
    return new AgendaWindowDto(from, to, assembler.toDtos(found), List.of());
  }

  /**
   * Conflits actifs sur [from, to[ (hors {@code excludeAppointmentId}), et rendez-vous actifs
   * précédent et suivant du même jour calendaire que {@code from} dans le fuseau {@code tz}.
   */
  public FreeBusyDto freeBusy(Instant from, Instant to, UUID excludeAppointmentId, ZoneId tz) {
    UUID practitionerId = me();
    UUID exclude = excludeAppointmentId == null ? NOTHING : excludeAppointmentId;
    LocalDate day = from.atZone(tz).toLocalDate();
    OffsetDateTime dayStart = day.atStartOfDay(tz).toOffsetDateTime();
    OffsetDateTime dayEnd = day.plusDays(1).atStartOfDay(tz).toOffsetDateTime();

    List<Appointment> conflicts =
        appointments.findActiveOverlaps(practitionerId, utc(from), utc(to), exclude);
    Appointment previous =
        appointments
            .findEndingBefore(practitionerId, dayStart, utc(from), ACTIVE, exclude, Limit.of(1))
            .stream()
            .findFirst()
            .orElse(null);
    Appointment next =
        appointments
            .findStartingAfter(practitionerId, utc(to), dayEnd, ACTIVE, exclude, Limit.of(1))
            .stream()
            .findFirst()
            .orElse(null);
    Map<UUID, PatientRef> refs = assembler.patientsOf(practitionerId);
    return new FreeBusyDto(
        assembler.conflicts(conflicts, refs),
        previous == null ? null : assembler.toDto(previous, refs),
        next == null ? null : assembler.toDto(next, refs));
  }

  /**
   * Rendez-vous actifs du praticien courant pour ce patient, non terminés à {@code from} (défaut :
   * maintenant), triés par début, au plus {@code limit} ; plus les défauts de création (G Q56).
   * Garde de propriété d'abord (403 / 404 des patients).
   */
  public PatientAppointmentsDto ofPatient(UUID patientId, Instant from, int limit) {
    guard.requireOwned(patientId);
    UUID practitionerId = me();
    Instant since = from == null ? clock.instant() : from;
    List<Appointment> found =
        appointments.findOfPatient(practitionerId, patientId, utc(since), ACTIVE, Limit.of(limit));
    return new PatientAppointmentsDto(
        assembler.toDtos(found),
        defaults(practitionerId, patients.followUp(patientId).orElse(null)));
  }

  /**
   * G Q56 : durée du suivi, sinon préférence {@code sessions.defaultDurationMin}, sinon 60 ;
   * modalité du suivi si elle est une modalité de rendez-vous, sinon {@code in_person} ; rythme tel
   * quel.
   */
  private AppointmentDefaultsDto defaults(UUID practitionerId, FollowUpView followUp) {
    Integer duration = followUp == null ? null : followUp.durationMin();
    if (duration == null) {
      duration =
          practitioners.preferencesOf(practitionerId).get(DEFAULT_DURATION_PREFERENCE)
                  instanceof Number n
              ? n.intValue()
              : FALLBACK_DURATION_MIN;
    }
    String modality = followUp == null ? null : followUp.modality();
    boolean known =
        modality != null
            && Arrays.stream(AppointmentModality.values()).anyMatch(m -> m.name().equals(modality));
    return new AppointmentDefaultsDto(
        duration,
        known ? modality : AppointmentModality.in_person.name(),
        followUp == null ? null : followUp.rhythm());
  }
}
