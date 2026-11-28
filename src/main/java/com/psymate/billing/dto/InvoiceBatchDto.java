package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Brouillons créés par lot (L5.3), un par patient. */
@Schema(
    description =
        "Brouillons créés, un par patient, dans l'ordre où chaque patient apparaît la première"
            + " fois dans la demande.")
public record InvoiceBatchDto(
    @Schema(description = "Brouillons créés.") List<InvoiceBatchItemDto> invoices) {}
