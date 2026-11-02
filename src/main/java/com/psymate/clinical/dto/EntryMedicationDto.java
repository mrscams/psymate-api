package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Détail d'un médicament (type medication).")
public record EntryMedicationDto(
    @Schema(description = "Molécule ou nom du médicament.", example = "sertraline") String molecule,
    @Schema(description = "Posologie, texte libre.", example = "50 mg") String dosage,
    @Schema(description = "Fréquence de prise, texte libre.", example = "1 fois par jour")
        String frequency,
    @Schema(description = "Prescripteur, texte libre.", example = "Dr Martin") String prescriber,
    @Schema(
            description = "Début de la période de prise, date ISO 8601 (AAAA-MM-JJ).",
            example = "2026-09-01")
        LocalDate periodFrom,
    @Schema(
            description =
                "Fin de la période de prise, date ISO 8601 (AAAA-MM-JJ). Null si en cours.",
            example = "2026-12-01")
        LocalDate periodTo) {}
