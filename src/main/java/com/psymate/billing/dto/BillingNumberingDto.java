package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Numérotation des factures et des avoirs (G Q89). */
@Schema(
    description =
        "Numérotation continue, sans trou, par nature et par année civile (Europe/Paris) : préfixe,"
            + " année, numéro sur quatre chiffres. Aucun champ ne permet de choisir le prochain"
            + " numéro.")
public record BillingNumberingDto(
    @Schema(description = "Préfixe des factures, 10 caractères au plus.", example = "F")
        String invoicePrefix,
    @Schema(description = "Préfixe des avoirs, 10 caractères au plus.", example = "AV")
        String creditNotePrefix,
    @Schema(
            description =
                "Prochain numéro de facture de l'année en cours, à titre indicatif : il est"
                    + " attribué à l'émission.",
            example = "F-2026-0001")
        String nextInvoiceNumber,
    @Schema(
            description = "Prochain numéro d'avoir de l'année en cours, à titre indicatif.",
            example = "AV-2026-0001")
        String nextCreditNoteNumber,
    @Schema(
            description =
                "Vrai dès qu'un numéro (facture ou avoir) a été attribué : les préfixes ne"
                    + " changent plus (409 NUMBERING_LOCKED).")
        boolean locked) {}
