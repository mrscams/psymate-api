package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(
    description =
        "Démarrage d'une séance maintenant. authorType et submissionId sont refusés"
            + " (UNKNOWN_FIELD).")
public record StartSessionRequest(
    @Schema(
            description = "Modalité de la séance. Obligatoire.",
            allowableValues = {"in_person", "video", "phone", "email"},
            example = "in_person")
        @NotBlank
        @Pattern(regexp = "in_person|video|phone|email")
        String modality)
    implements ClinicalRequest {}
