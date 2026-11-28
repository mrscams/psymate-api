package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** Modèles d'e-mail (PUT des réglages). */
@Schema(
    description =
        "Modèles d'e-mail : envoi d'une facture (invoice) et relance (reminder). Absent ou null :"
            + " textes par défaut.")
public record BillingEmailTemplatesRequest(
    @Valid BillingEmailTemplateRequest invoice, @Valid BillingEmailTemplateRequest reminder) {}
