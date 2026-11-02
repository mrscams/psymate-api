package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
    description =
        "Moment auquel l'entrée a eu lieu. Remplacé en entier lors d'un PATCH. Absent à la"
            + " création : l'instant de saisie est utilisé (sauf pour une situation, où il est"
            + " obligatoire).")
public record EntryOccurredRequest(
    @Schema(
            description =
                "Début de l'occurrence, date-heure UTC ISO 8601. Obligatoire (REQUIRED) sauf"
                    + " pour la précision life_phase.",
            example = "2026-10-20T19:00:00Z")
        Instant from,
    @Schema(
            description =
                "Fin de l'occurrence, date-heure UTC ISO 8601, jamais avant from (INVALID_TIME"
                    + " sur occurred.to). Absente : pour une situation, elle est en cours ;"
                    + " pour les autres types, égale à from (la précision period exige une"
                    + " fin).",
            example = "2026-10-20T20:00:00Z")
        Instant to,
    @Schema(
            description =
                "Précision de la date : exact (défaut) = date-heure connue ; day = jour connu ;"
                    + " month = mois connu ; year = année connue ; period = période libre"
                    + " (libellé obligatoire) ; life_phase = phase de vie reconnue par son"
                    + " libellé (enfance), bornes déduites de la date de naissance du patient."
                    + " Toute autre valeur : INVALID_VALUE sur occurred.precision.",
            allowableValues = {"exact", "day", "month", "year", "period", "life_phase"},
            defaultValue = "exact",
            example = "exact")
        String precision,
    @Schema(
            description =
                "Libellé de la période (precision period) ou de la phase de vie (precision"
                    + " life_phase).",
            example = "enfance")
        String label) {}
