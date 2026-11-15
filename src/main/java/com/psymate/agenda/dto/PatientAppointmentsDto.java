package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Rendez-vous à venir d'un patient et défauts de création (I3, G Q56). */
@Schema(
    description =
        "Rendez-vous actifs d'un patient à partir d'un instant, et les valeurs à préremplir pour"
            + " en créer un nouveau.")
public record PatientAppointmentsDto(
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Rendez-vous actifs (planned, tentative) du praticien courant pour ce"
                            + " patient, non terminés à l'instant from, triés par début."))
        List<AppointmentDto> appointments,
    @Schema(description = "Défauts de création d'un rendez-vous pour ce patient.")
        AppointmentDefaultsDto defaults) {}
