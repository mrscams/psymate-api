package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Annulation d'un rendez-vous (I3) : motif facultatif. */
@Schema(description = "Annulation d'un rendez-vous. Le corps est facultatif.")
public record CancelAppointmentRequest(
    @Schema(
            description = "Motif d'annulation, 500 caractères au plus. Facultatif.",
            maxLength = 500)
        @Size(max = 500)
        String reason) {}
