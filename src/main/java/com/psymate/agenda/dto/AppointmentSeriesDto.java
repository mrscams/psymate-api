package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Occurrences d'une série, état final (annulation de série, I3). */
@Schema(description = "Toutes les occurrences de la série du praticien, triées par début.")
public record AppointmentSeriesDto(
    @Schema(
            description =
                "Occurrences de la série dans leur état final : les actives sont annulées, les"
                    + " terminées (done) et déjà annulées restent telles quelles.")
        List<AppointmentDto> appointments) {}
