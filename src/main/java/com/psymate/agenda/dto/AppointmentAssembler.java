package com.psymate.agenda.dto;

import com.psymate.agenda.model.Appointment;
import com.psymate.patients.api.PatientFacade;
import com.psymate.patients.api.PatientRef;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Composition rendez-vous + patient (G Q46 : mapping écrit à la main, donc {@code *Assembler}). Le
 * patient vient de la façade des patients ({@link PatientRef} : nom affiché, date de naissance,
 * archivé), jamais d'une table du module patients. Les appelants ont déjà vérifié la propriété du
 * patient (garde) ou ne lisent que des rendez-vous du praticien courant.
 *
 * <p>La façade n'expose l'indicateur « archivé » que par la liste des patients du praticien : le
 * service la lit une fois par requête ({@link #patientsOf}) et la passe aux surcharges qui la
 * prennent, quel que soit le nombre de rendez-vous à composer.
 *
 * <p>Un rendez-vous invité (G Q60) n'a pas de bloc patient : son bloc {@code guest} vient des
 * colonnes du rendez-vous, et son nom affiché dans un conflit est « Prénom Nom », ou {@link
 * #PURGED_GUEST} après la purge.
 */
@Component
public class AppointmentAssembler {

  /** Nom affiché d'un invité purgé dans un conflit (G Q60). */
  public static final String PURGED_GUEST = "Invité purgé";

  private final PatientFacade patients;
  private final AppointmentMapper mapper;

  public AppointmentAssembler(PatientFacade patients, AppointmentMapper mapper) {
    this.patients = patients;
    this.mapper = mapper;
  }

  /** Patients du praticien, indexés par identifiant. */
  public Map<UUID, PatientRef> patientsOf(UUID practitionerId) {
    Map<UUID, PatientRef> byId = new HashMap<>();
    for (PatientRef ref : patients.ownedBy(practitionerId)) {
      byId.put(ref.id(), ref);
    }
    return byId;
  }

  /** Un rendez-vous ; lit la liste des patients de son praticien. */
  public AppointmentDto toDto(Appointment appointment) {
    return toDto(appointment, patientsOf(appointment.getPractitionerId()));
  }

  /** Un rendez-vous, avec la liste des patients déjà lue par l'appelant ({@link #patientsOf}). */
  public AppointmentDto toDto(Appointment appointment, Map<UUID, PatientRef> refs) {
    if (appointment.isGuest()) {
      return mapper.toDto(appointment, null, guest(appointment));
    }
    return mapper.toDto(appointment, patient(appointment.getPatientId(), refs), null);
  }

  /** Rendez-vous d'un même praticien : une seule lecture des patients. */
  public List<AppointmentDto> toDtos(Collection<Appointment> appointments) {
    if (appointments.isEmpty()) {
      return List.of();
    }
    Map<UUID, PatientRef> refs = patientsOf(appointments.iterator().next().getPractitionerId());
    return appointments.stream().map(a -> toDto(a, refs)).toList();
  }

  /**
   * Chevauchements signalés (G Q54) du créneau demandé qui commence à {@code occurrenceStartsAt},
   * avec la liste des patients du praticien déjà lue par l'appelant ({@link #patientsOf}).
   */
  public List<ConflictDto> conflicts(
      Collection<Appointment> overlapping, Map<UUID, PatientRef> refs, Instant occurrenceStartsAt) {
    return overlapping.stream()
        .map(
            a ->
                new ConflictDto(
                    a.getId(),
                    a.getStartsAt().toInstant(),
                    a.getEndsAt().toInstant(),
                    displayName(a, refs),
                    occurrenceStartsAt))
        .toList();
  }

  /** Nom affiché dans un conflit : celui du patient, ou celui de l'invité (G Q60). */
  private String displayName(Appointment a, Map<UUID, PatientRef> refs) {
    if (!a.isGuest()) {
      return patient(a.getPatientId(), refs).displayName();
    }
    return guestDisplayName(a.getGuestFirstName(), a.getGuestLastName(), a.isGuestPurged());
  }

  /** « Prénom Nom », le nom seul sans prénom, « Invité purgé » après la purge. */
  public static String guestDisplayName(String firstName, String lastName, boolean purged) {
    if (purged || lastName == null) {
      return PURGED_GUEST;
    }
    return firstName == null || firstName.isBlank() ? lastName : firstName + " " + lastName;
  }

  private static GuestDto guest(Appointment a) {
    return new GuestDto(
        a.getGuestFirstName(),
        a.getGuestLastName(),
        a.getGuestPhone(),
        a.getGuestPurgedAt() == null ? null : a.getGuestPurgedAt().toInstant());
  }

  /**
   * Patient de la liste du praticien ; à défaut (patient qui n'y figure plus), lecture unitaire du
   * nom et de la date de naissance, non archivé.
   */
  private AppointmentPatientDto patient(UUID patientId, Map<UUID, PatientRef> refs) {
    PatientRef ref = refs.get(patientId);
    if (ref != null) {
      return new AppointmentPatientDto(
          ref.id(), ref.displayName(), ref.birthDate(), ref.archived());
    }
    return new AppointmentPatientDto(
        patientId,
        patients.displayName(patientId).orElse(null),
        patients.birthDate(patientId).orElse(null),
        false);
  }
}
