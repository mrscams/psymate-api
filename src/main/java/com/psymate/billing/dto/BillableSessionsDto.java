package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Séances à facturer du praticien, par patient (L5.2). */
@Schema(
    description =
        "Séances à facturer (état pending) du praticien, une entrée par patient, par nom affiché.")
public record BillableSessionsDto(
    @Schema(description = "Groupes par patient, par nom affiché (sans tenir compte des accents).")
        List<BillableSessionGroupDto> groups,
    @Schema(
            description =
                "Nombre de séances écartées (« ne pas facturer ») des 90 derniers jours, pour le"
                    + " patient demandé s'il y en a un.",
            example = "2")
        long dismissedCount) {}
