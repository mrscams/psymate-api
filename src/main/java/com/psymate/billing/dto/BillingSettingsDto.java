package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Réglages de facturation du praticien (L5.1, M15-06). */
@Schema(
    description =
        "Réglages de facturation du praticien. Ils existent toujours : sans réglages enregistrés,"
            + " valeurs par défaut, adresse et e-mail proposés depuis le dossier professionnel.")
public record BillingSettingsDto(
    BillingIssuerIdentityDto identity,
    @Schema(
            description = "SIRET, 14 chiffres ; null s'il n'est pas renseigné.",
            example = "73282932000074")
        String siret,
    @Schema(
            description =
                "Adresse du cabinet sur les factures, lignes séparées par des sauts de ligne. Tant"
                    + " qu'elle n'est pas enregistrée, adresse du dossier professionnel proposée"
                    + " (null s'il n'en a pas).",
            maxLength = 300)
        String addressLines,
    @Schema(
            description =
                "E-mail affiché sur les factures. Tant qu'il n'est pas enregistré, e-mail du compte"
                    + " proposé.",
            maxLength = 320)
        String displayEmail,
    @Schema(
            description = "Téléphone affiché, facultatif ; null s'il n'est pas renseigné.",
            maxLength = 30)
        String displayPhone,
    BillingVatDto vat,
    BillingNumberingDto numbering,
    BillingPaymentTermsDto payment,
    @Schema(
            description = "Mention au bas de chaque facture, 500 caractères au plus ; null sinon.",
            maxLength = 500)
        String footerNote,
    BillingEmailTemplatesDto emailTemplates,
    @Schema(
            description =
                "Ce qui empêche d'émettre une facture, dans cet ordre : siret (absent),"
                    + " addressLines (aucune adresse, ni enregistrée ni proposée), service (aucune"
                    + " prestation active). Vide quand tout est prêt.")
        List<String> missing) {}
