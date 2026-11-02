package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Médicament en cours.")
public record ActiveMedicationDto(
    @Schema(
            description = "Identifiant de l'entrée médicament.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID entryId,
    @Schema(description = "Molécule.", example = "sertraline") String molecule,
    @Schema(description = "Dosage ; null si non saisi.", example = "50 mg") String dosage,
    @Schema(description = "Fréquence de prise ; null si non saisie.", example = "matin")
        String frequency,
    @Schema(description = "Prescripteur ; null si non saisi.", example = "Dr Martin")
        String prescriber,
    @Schema(
            description = "Début de l'entrée (occurred_from), UTC ISO 8601.",
            example = "2026-01-10T08:00:00Z")
        Instant since,
    @Schema(
            description = "Début de la période de prise AAAA-MM-JJ ; null si non saisi.",
            example = "2026-01-10")
        LocalDate periodFrom,
    @Schema(
            description =
                "Fin de la période de prise AAAA-MM-JJ ; null si non saisie (traitement en"
                    + " cours).",
            example = "2026-06-30")
        LocalDate periodTo) {}
