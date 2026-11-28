package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Facture annulée par un avoir (L5.3 {@code cancels}). */
@Schema(description = "Facture que cet avoir annule.")
public record InvoiceRefDto(
    @Schema(description = "Identifiant de la facture.") UUID id,
    @Schema(description = "Numéro de la facture.", example = "F-2026-0042") String number) {}
