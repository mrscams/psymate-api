package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Création d'un rendez-vous depuis un créneau, ou d'une série avec {@code recurrence} (fiche 15b,
 * I3, G Q55). {@code tz} ne sert qu'au calcul des occurrences d'une série.
 */
@Schema(
    description =
        "Création d'un rendez-vous, ou d'une série si recurrence est présent. status,"
            + " practitionerId, tenantId, sessionEntryId, authorType et submissionId sont refusés"
            + " (UNKNOWN_FIELD).")
public record CreateAppointmentRequest(
    @Schema(description = "Patient du praticien, non archivé. Obligatoire.") @NotNull
        UUID patientId,
    @Schema(
            description = "Début du rendez-vous, UTC ISO 8601. Obligatoire.",
            example = "2026-11-16T09:00:00Z")
        @NotNull
        Instant startsAt,
    @Schema(
            description = "Durée en minutes, de 5 à 600. Obligatoire.",
            minimum = "5",
            maximum = "600",
            example = "50")
        @NotNull
        @Min(5)
        @Max(600)
        Integer durationMin,
    @Schema(
            description = "Modalité. Obligatoire.",
            allowableValues = {"in_person", "video", "phone"},
            example = "in_person")
        @NotNull
        @Pattern(regexp = "in_person|video|phone")
        String modality,
    @Schema(
            description = "Note de planification, 2 000 caractères au plus. Facultative.",
            maxLength = 2000)
        @Size(max = 2000)
        String planningNote,
    @Schema(
            description =
                "true pour « À confirmer » (statut tentative, appliqué à toutes les occurrences)."
                    + " Défaut : false.")
        Boolean tentative,
    @Schema(
            description =
                "Répétition (G Q55) : une ligne par occurrence, même seriesId, même statut, même"
                    + " note. Absente : un seul rendez-vous.")
        @Valid
        RecurrenceRequest recurrence,
    @Schema(
            description =
                "Fuseau IANA dans lequel les occurrences d'une série gardent la même heure locale"
                    + " et dans lequel until est lu (INVALID_VALUE si inconnu). Défaut :"
                    + " Europe/Paris.",
            example = "Europe/Paris")
        String tz) {}
