package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Émetteur d'une facture (L5.3 {@code issuer}) : pour un brouillon, les réglages du moment ; pour
 * un document émis, l'instantané de l'émission.
 */
@Schema(
    description =
        "Émetteur : pour un brouillon, les réglages de facturation du moment ; pour un document"
            + " émis, l'instantané pris à l'émission.")
public record InvoiceIssuerDto(
    @Schema(description = "Nom affiché du praticien.", example = "Dr Maya Kaddour") String name,
    @Schema(description = "Titre imprimé de la profession.", example = "Psychologue") String title,
    @Schema(description = "Numéro professionnel (ADELI, RPPS). Null s'il n'est pas renseigné.")
        String professionalNumber,
    @Schema(description = "SIRET (14 chiffres). Null s'il n'est pas renseigné.") String siret,
    @Schema(description = "Adresse, lignes séparées par des sauts de ligne.") String addressLines,
    @Schema(description = "E-mail affiché.") String displayEmail,
    @Schema(description = "Téléphone affiché.") String displayPhone,
    @Schema(
            description =
                "Mention légale du régime de TVA (G Q92) ; null en régime assujetti, qui imprime"
                    + " HT, TVA et TTC.",
            example = "TVA non applicable, article 261-4-1° du Code général des impôts")
        String vatMention,
    @Schema(
            description = "Taux de TVA en points de base (2000 = 20 %), en régime assujetti.",
            example = "2000")
        Integer vatRateBp,
    @Schema(description = "Numéro de TVA intracommunautaire, en régime assujetti.")
        String vatNumber,
    @Schema(description = "Ordre des chèques.") String chequePayee,
    @Schema(description = "Modes de paiement acceptés, parmi transfer, cheque, cash, card, other.")
        List<String> acceptedModes,
    @Schema(
            description =
                "Coordonnées bancaires imprimées : réglage activé et IBAN enregistré (l'IBAN"
                    + " n'est jamais renvoyé).")
        boolean bankDetailsShown) {}
