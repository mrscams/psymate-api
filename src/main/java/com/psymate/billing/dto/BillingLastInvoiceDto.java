package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Dernière facture émise d'un patient (L5.2 {@code lastInvoice}). */
@Schema(description = "Dernière facture émise du patient : numéro et date d'émission.")
public record BillingLastInvoiceDto(
    @Schema(description = "Numéro de la facture.", example = "F-2026-0042") String number,
    @Schema(description = "Date d'émission (AAAA-MM-JJ).", example = "2026-10-31")
        LocalDate issueDate) {}
