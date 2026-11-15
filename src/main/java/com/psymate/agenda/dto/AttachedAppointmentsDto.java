package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Rendez-vous rattachés à un patient par {@code attach-patient} (G Q60). */
@Schema(description = "Rendez-vous rattachés au patient, triés par début.")
public record AttachedAppointmentsDto(
    @Schema(
            description =
                "Le rendez-vous demandé et, avec includeSameGuest, les autres rendez-vous du même"
                    + " invité, dans leur état final (patient renseigné, guest null).")
        List<AppointmentDto> appointments) {}
