package com.psymate.agenda.dto;

import com.psymate.agenda.model.Appointment;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Rendez-vous du domaine vers DTO REST (MapStruct, G Q46). Le bloc patient n'est pas calculé ici :
 * {@link AppointmentAssembler} le compose avec la façade des patients et le passe tel quel.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AppointmentMapper {

  @Mapping(target = "id", source = "appointment.id")
  @Mapping(target = "patient", source = "patient")
  AppointmentDto toDto(Appointment appointment, AppointmentPatientDto patient);

  /** Instants exposés en UTC (convention REST du projet). */
  default Instant instant(OffsetDateTime t) {
    return t == null ? null : t.toInstant();
  }
}
