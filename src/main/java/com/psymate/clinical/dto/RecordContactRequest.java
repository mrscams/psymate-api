package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

@Schema(
    description =
        "Contact hors rendez-vous (téléphone, e-mail), consigné comme une séance déjà clôturée."
            + " authorType et submissionId sont refusés (UNKNOWN_FIELD).")
public record RecordContactRequest(
    @Schema(
            description = "Modalité du contact. Obligatoire.",
            allowableValues = {"phone", "email"},
            example = "phone")
        @NotBlank
        @Pattern(regexp = "phone|email")
        String modality,
    @Schema(
            description = "Date-heure du contact, UTC ISO 8601. Obligatoire.",
            example = "2026-10-22T08:30:00Z")
        @NotNull
        Instant occurredFrom,
    @Schema(description = "Texte du contact, qui devient le compte rendu. Obligatoire.") @NotBlank
        String body)
    implements ClinicalRequest {}
