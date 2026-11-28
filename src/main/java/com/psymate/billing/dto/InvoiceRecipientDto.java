package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Destinataire d'une facture (L5.3 {@code recipient}). */
@Schema(
    description =
        "Destinataire : posé à la création depuis le patient (ou le tiers payeur) et son profil"
            + " de facturation, modifiable sur un brouillon.")
public record InvoiceRecipientDto(
    @Schema(description = "Nom du destinataire.", example = "Hélène Roux") String name,
    @Schema(description = "Adresse, lignes séparées par des sauts de ligne. Null sans adresse.")
        String addressLines,
    @Schema(
            description =
                "Nom du patient quand le destinataire est quelqu'un d'autre (tiers payeur) ; null"
                    + " sinon.")
        String patientName) {}
