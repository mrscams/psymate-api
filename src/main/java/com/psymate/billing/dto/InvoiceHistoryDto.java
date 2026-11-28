package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/** Événement de l'historique d'une facture (L5.3 {@code history}). */
@Schema(description = "Événement de l'historique de la facture.")
public record InvoiceHistoryDto(
    @Schema(
            description =
                "Nature : draft_created, issued, sent, payment_recorded, payment_cancelled,"
                    + " credit_note_issued, voided.",
            example = "draft_created")
        String kind,
    @Schema(description = "Instant de l'événement, ISO 8601 UTC.") Instant at,
    @Schema(
            description =
                "Détail de l'événement ; pour draft_created : sessionCount (lignes de séance à la"
                    + " création) et batch (créé par lot).")
        Map<String, Object> detail) {}
