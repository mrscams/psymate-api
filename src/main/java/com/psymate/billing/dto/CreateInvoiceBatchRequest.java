package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Création de brouillons par lot (POST /api/invoices/batch, L5.3) : un brouillon par patient des
 * séances, tout ou rien. Tout autre champ est refusé (UNKNOWN_FIELD).
 */
@Schema(
    description =
        "Séances à facturer, de 1 à 200, sans doublon : un brouillon par patient, tout ou rien."
            + " Tout autre champ est refusé (UNKNOWN_FIELD).")
public record CreateInvoiceBatchRequest(
    @ArraySchema(
            arraySchema =
                @Schema(
                    description = "Séances à facturer, de 1 à 200, sans doublon.",
                    requiredMode = Schema.RequiredMode.REQUIRED),
            minItems = 1,
            maxItems = 200)
        @NotEmpty
        @Size(max = 200)
        List<@NotNull UUID> sessionEntryIds) {}
