package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Remplacement des réglages de facturation (PUT, L5.1) : tous les champs modifiables à la fois. Un
 * texte absent, null ou vide est effacé ; un bloc absent ou null prend ses valeurs par défaut. Les
 * champs en lecture seule de la réponse (identity, vat.mention, numbering.next*, numbering.locked,
 * payment.ibanLast4, missing) et tout autre champ sont refusés (UNKNOWN_FIELD).
 */
@Schema(
    description =
        "Réglages complets : remplacent l'existant. Un texte absent, null ou vide est effacé ; un"
            + " bloc absent ou null (vat, numbering, payment, emailTemplates) prend ses valeurs par"
            + " défaut. Les champs en lecture seule de la réponse (identity, vat.mention,"
            + " numbering.nextInvoiceNumber, numbering.nextCreditNoteNumber, numbering.locked,"
            + " payment.ibanLast4, missing) et tout autre champ sont refusés (UNKNOWN_FIELD).")
public record BillingSettingsRequest(
    @Schema(
            description =
                "SIRET : 14 chiffres dont la clé de Luhn (SIRET_INVALID sinon) ; les espaces sont"
                    + " retirés.",
            example = "73282932000074")
        String siret,
    @Schema(description = "Adresse du cabinet, 300 caractères au plus.", maxLength = 300)
        @Size(max = 300)
        String addressLines,
    @Schema(description = "E-mail affiché (INVALID_VALUE s'il est mal formé).", maxLength = 320)
        @Size(max = 320)
        String displayEmail,
    @Schema(
            description =
                "Téléphone affiché : chiffres, espaces, +, parenthèses, points et tirets.",
            maxLength = 30)
        @Size(max = 30)
        String displayPhone,
    @Valid BillingVatRequest vat,
    @Valid BillingNumberingRequest numbering,
    @Valid BillingPaymentTermsRequest payment,
    @Schema(
            description = "Mention au bas de chaque facture, 500 caractères au plus.",
            maxLength = 500)
        @Size(max = 500)
        String footerNote,
    @Valid BillingEmailTemplatesRequest emailTemplates) {}
