package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Rendez-vous actif du même praticien qui chevauche le créneau demandé (G Q54) : signalé, jamais
 * bloquant.
 */
@Schema(
    description =
        "Rendez-vous actif (planned ou tentative) du praticien qui chevauche le créneau : un"
            + " avertissement, jamais un refus.")
public record ConflictDto(
    @Schema(description = "Identifiant du rendez-vous chevauché.") UUID appointmentId,
    @Schema(description = "Début du rendez-vous chevauché, UTC ISO 8601.") Instant startsAt,
    @Schema(description = "Fin du rendez-vous chevauché, UTC ISO 8601.") Instant endsAt,
    @Schema(description = "Nom affiché du patient du rendez-vous chevauché.", example = "Théo B.")
        String patientDisplayName) {}
