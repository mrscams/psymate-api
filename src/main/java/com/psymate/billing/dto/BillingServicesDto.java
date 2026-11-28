package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Prestations non archivées du praticien (L5.1). */
@Schema(
    description =
        "Prestations non archivées du praticien : la prestation par défaut d'abord, puis par"
            + " libellé.")
public record BillingServicesDto(
    @Schema(description = "Prestations, 50 au plus.") List<BillingServiceDto> services) {}
