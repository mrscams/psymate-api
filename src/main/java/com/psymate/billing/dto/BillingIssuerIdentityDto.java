package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Identité du praticien imprimée sur les factures (L5.1), lue dans identity, non modifiable ici.
 */
@Schema(
    description =
        "Identité du praticien sur les factures, en lecture seule : elle vient du dossier"
            + " professionnel et se modifie là-bas.")
public record BillingIssuerIdentityDto(
    @Schema(description = "Nom affiché du praticien.", example = "Dr Maya Kaddour") String name,
    @Schema(
            description =
                "Titre tiré de la profession, en français comme sur la facture (Psychologue,"
                    + " Psychiatre, Coach). Null si la profession est inconnue.",
            example = "Psychologue")
        String title,
    @Schema(
            description = "Numéro professionnel (ADELI ou RPPS) ; null s'il n'est pas renseigné.",
            example = "689712345")
        String professionalNumber) {}
