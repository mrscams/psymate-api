package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Brouillon créé par lot (L5.3 {@code POST /api/invoices/batch}). */
@Schema(description = "Brouillon créé pour un patient. Total en centimes d'euro entiers.")
public record InvoiceBatchItemDto(
    @Schema(description = "Identifiant du brouillon.") UUID id,
    @Schema(description = "Patient du brouillon.") BillingPatientRefDto patient,
    @Schema(description = "Total du brouillon en centimes.", format = "int64", example = "18000")
        long total) {}
