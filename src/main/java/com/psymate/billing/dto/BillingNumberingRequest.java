package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Préfixes de numérotation (PUT des réglages, G Q89). */
@Schema(
    description =
        "Préfixes de numérotation. Absent, null ou vide : préfixe par défaut (F, AV). Lettres et"
            + " chiffres, tirets ou soulignés entre eux, 10 caractères au plus ; les deux préfixes"
            + " diffèrent. Ils ne changent plus dès qu'un numéro a été attribué (409"
            + " NUMBERING_LOCKED).")
public record BillingNumberingRequest(
    @Schema(description = "Préfixe des factures.", maxLength = 10, example = "F") @Size(max = 10)
        String invoicePrefix,
    @Schema(description = "Préfixe des avoirs.", maxLength = 10, example = "AV") @Size(max = 10)
        String creditNotePrefix) {}
