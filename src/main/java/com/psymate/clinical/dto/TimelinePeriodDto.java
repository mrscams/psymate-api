package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/** Une période du résumé : « 2026-10 » (mois) ou « 2026 » (année), début en UTC. */
@Schema(description = "Agrégats d'une période (un mois ou une année, en UTC).")
public record TimelinePeriodDto(
    @Schema(
            description = "Libellé de la période : AAAA-MM pour un mois, AAAA pour une année.",
            example = "2026-10")
        String period,
    @Schema(description = "Début de la période, UTC ISO 8601.", example = "2026-10-01T00:00:00Z")
        Instant from,
    @Schema(description = "Nombre d'entrées de la période par type (clé = code du type).")
        Map<String, Integer> counts,
    @Schema(description = "Nombre de ressentis de la période.", minimum = "0") int feelingCount,
    @Schema(description = "Intensité moyenne des ressentis de la période. Null s'il n'y en a pas.")
        Double averageIntensity) {}
