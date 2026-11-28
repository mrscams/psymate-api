package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Séances écartées des 90 derniers jours, sans regroupement (L5.2). */
@Schema(
    description =
        "Séances écartées (« ne pas facturer ») des 90 derniers jours, la plus récente d'abord.")
public record DismissedSessionsDto(
    @Schema(description = "Séances écartées, chacune avec son patient.")
        List<DismissedSessionDto> sessions) {}
