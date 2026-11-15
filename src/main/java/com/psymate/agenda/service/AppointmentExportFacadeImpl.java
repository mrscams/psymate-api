package com.psymate.agenda.service;

import com.psymate.agenda.api.AppointmentExportFacade;
import com.psymate.agenda.api.AppointmentExportView;
import com.psymate.agenda.dto.AppointmentAssembler;
import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.repository.AppointmentRepository;
import com.psymate.patients.api.PatientFacade;
import com.psymate.patients.api.PatientRef;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecture des rendez-vous pour l'export (G Q61, M11-11) : JPQL de {@link AppointmentRepository},
 * transaction en lecture seule, aucune écriture. Le nom affiché d'un patient vient de {@link
 * PatientFacade} ; celui d'un invité suit la règle de {@link AppointmentAssembler#guestDisplayName}
 * (« Prénom Nom », ou « Invité purgé »). Aucun contrôle d'accès : voir {@link
 * AppointmentExportFacade}.
 */
@Service
@Transactional(readOnly = true)
class AppointmentExportFacadeImpl implements AppointmentExportFacade {

  private final AppointmentRepository appointments;
  private final PatientFacade patients;

  AppointmentExportFacadeImpl(AppointmentRepository appointments, PatientFacade patients) {
    this.appointments = appointments;
    this.patients = patients;
  }

  @Override
  public List<AppointmentExportView> allOfPractitioner(UUID practitionerId) {
    List<Appointment> rows = appointments.findAllForExport(practitionerId);
    if (rows.isEmpty()) {
      return List.of();
    }
    // Une seule lecture de la liste des patients du praticien ; lecture unitaire à défaut.
    Map<UUID, String> names = new HashMap<>();
    for (PatientRef ref : patients.ownedBy(practitionerId)) {
      names.put(ref.id(), ref.displayName());
    }
    Function<UUID, String> nameOf =
        id -> names.computeIfAbsent(id, k -> patients.displayName(k).orElse(null));
    return rows.stream().map(a -> view(a, nameOf)).toList();
  }

  @Override
  public List<AppointmentExportView> ofPatient(UUID patientId) {
    List<Appointment> rows = appointments.findAllOfPatientForExport(patientId);
    if (rows.isEmpty()) {
      return List.of();
    }
    String name = patients.displayName(patientId).orElse(null);
    return rows.stream().map(a -> view(a, id -> name)).toList();
  }

  private static AppointmentExportView view(Appointment a, Function<UUID, String> patientName) {
    String subject;
    String phone = null;
    if (a.isGuest()) {
      subject =
          AppointmentAssembler.guestDisplayName(
              a.getGuestFirstName(), a.getGuestLastName(), a.isGuestPurged());
      phone = a.isGuestPurged() ? null : a.getGuestPhone();
    } else {
      subject = patientName.apply(a.getPatientId());
    }
    return new AppointmentExportView(
        a.getId(),
        a.getPatientId(),
        subject,
        phone,
        a.getStartsAt().toInstant(),
        a.getEndsAt().toInstant(),
        a.getDurationMin(),
        a.getModality().name(),
        a.getStatus().name(),
        a.getSeriesId(),
        a.getPlanningNote(),
        a.getCancelReason(),
        instant(a.getCancelledAt()));
  }

  private static Instant instant(OffsetDateTime at) {
    return at == null ? null : at.toInstant();
  }
}
