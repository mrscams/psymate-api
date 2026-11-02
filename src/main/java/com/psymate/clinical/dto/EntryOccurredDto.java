package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
    description =
        "Moment auquel l'entrée a eu lieu, avec sa précision. Les bornes sont en UTC. Pour une"
            + " précision autre qu'exact, les bornes sont celles de la période désignée (jour,"
            + " mois, année entiers).")
public record EntryOccurredDto(
    @Schema(
            description = "Début de l'occurrence, date-heure UTC ISO 8601.",
            example = "2026-10-20T19:00:00Z")
        Instant from,
    @Schema(
            description =
                "Fin de l'occurrence, date-heure UTC ISO 8601. Null pour une situation en cours"
                    + " (sans fin).",
            example = "2026-10-20T19:00:00Z")
        Instant to,
    @Schema(
            description =
                "Précision de la date : exact = date-heure connue ; day = jour connu ; month ="
                    + " mois connu ; year = année connue ; period = période libre avec libellé ;"
                    + " life_phase = phase de vie (bornes déduites de la date de naissance du"
                    + " patient).",
            allowableValues = {"exact", "day", "month", "year", "period", "life_phase"},
            example = "exact")
        String precision,
    @Schema(
            description =
                "Libellé de la période ou de la phase de vie (precision period ou life_phase)."
                    + " Null sinon.",
            example = "enfance")
        String label) {}
