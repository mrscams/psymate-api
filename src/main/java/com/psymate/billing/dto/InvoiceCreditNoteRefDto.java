package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/** Avoir émis sur une facture (L5.3 {@code creditNotes}). */
@Schema(description = "Avoir émis sur cette facture.")
public record InvoiceCreditNoteRefDto(
    @Schema(description = "Identifiant de l'avoir.") UUID id,
    @Schema(description = "Numéro de l'avoir.", example = "AV-2026-0003") String number,
    @Schema(description = "Total de l'avoir en centimes.", format = "int64", example = "6000")
        long total,
    @Schema(description = "Date d'émission (AAAA-MM-JJ).") LocalDate issueDate) {}
