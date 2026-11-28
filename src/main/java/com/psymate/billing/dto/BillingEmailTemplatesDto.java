package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Modèles d'e-mail du praticien (L5.1). */
@Schema(
    description =
        "Modèles d'e-mail du praticien. Sans modèle enregistré, le texte par défaut de"
            + " l'application, en français.")
public record BillingEmailTemplatesDto(
    BillingEmailTemplateDto invoice, BillingEmailTemplateDto reminder) {}
