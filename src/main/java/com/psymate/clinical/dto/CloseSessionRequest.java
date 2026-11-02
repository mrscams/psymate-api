package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(
    description =
        "Clôture d'une séance ouverte. authorType et submissionId sont refusés (UNKNOWN_FIELD).")
public record CloseSessionRequest(
    @Schema(
            description =
                "Durée de la séance en minutes, de 1 à 600 (OUT_OF_RANGE sinon). Absente : la"
                    + " durée par défaut du praticien, sinon 60 minutes.",
            minimum = "1",
            maximum = "600",
            example = "50")
        @Min(1)
        @Max(600)
        Integer durationMin,
    @Schema(
            description =
                "Compte rendu de la séance, texte libre. Vide ou absent : aucun compte rendu.")
        String summary)
    implements ClinicalRequest {}
