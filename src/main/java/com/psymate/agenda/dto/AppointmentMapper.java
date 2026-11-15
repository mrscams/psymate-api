package com.psymate.agenda.dto;

import com.psymate.agenda.model.Appointment;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Rendez-vous du domaine vers DTO REST (MapStruct, G Q46). Les blocs patient et invité ne sont pas
 * calculés ici : {@link AppointmentAssembler} les compose et les passe tels quels.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AppointmentMapper {

  @Mapping(target = "id", source = "appointment.id")
  @Mapping(target = "patient", source = "patient")
  @Mapping(target = "guest", source = "guest")
  AppointmentDto toDto(Appointment appointment, AppointmentPatientDto patient, GuestDto guest);

  /** Instants exposés en UTC (convention REST du projet). */
  default Instant instant(OffsetDateTime t) {
    return t == null ? null : t.toInstant();
  }
}
