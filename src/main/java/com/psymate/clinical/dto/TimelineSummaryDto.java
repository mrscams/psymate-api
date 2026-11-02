package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Résumé de la chronologie par mois ou par année. Seules les périodes qui contiennent au"
            + " moins une entrée sont renvoyées, de la plus ancienne à la plus récente.")
public record TimelineSummaryDto(
    @Schema(
            description = "Granularité demandée.",
            allowableValues = {"month", "year"},
            example = "month")
        String granularity,
    @Schema(description = "Périodes non vides, de la plus ancienne à la plus récente.")
        List<TimelinePeriodDto> periods) {}
