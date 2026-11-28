package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Régime de TVA du praticien et mention portée sur les factures (G Q92). */
@Schema(description = "Régime de TVA et mention portée sur chaque facture (G Q92).")
public record BillingVatDto(
    @Schema(
            description =
                "Régime : health_exempt (exonérée, soins à la personne), franchise (franchise en"
                    + " base) ou liable (assujettie : prix hors taxes, la facture montre HT, TVA,"
                    + " TTC).",
            allowableValues = {"health_exempt", "franchise", "liable"},
            example = "health_exempt")
        String regime,
    @Schema(
            description =
                "Taux de TVA en points de base (2000 = 20 %), de 0 à 10000. Renseigné en régime"
                    + " liable seulement, null sinon.",
            minimum = "0",
            maximum = "10000",
            example = "2000")
        Integer rateBp,
    @Schema(
            description =
                "Numéro de TVA intracommunautaire, en régime liable seulement, null sinon.",
            example = "FR40303265045")
        String vatNumber,
    @Schema(
            description =
                "Mention légale du régime, fournie par l'application et toujours en français, quelle"
                    + " que soit la langue servie : ce n'est pas un champ libre. health_exempt :"
                    + " « TVA non applicable, article 261-4-1° du Code général des impôts » ;"
                    + " franchise : « TVA non applicable, article 293 B du Code général des impôts »"
                    + " ; null en régime liable (aucune mention d'exonération).",
            example = "TVA non applicable, article 261-4-1° du Code général des impôts")
        String mention) {}
