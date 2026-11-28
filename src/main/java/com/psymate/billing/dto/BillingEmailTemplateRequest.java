package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Modèle d'un e-mail (PUT des réglages). */
@Schema(
    description =
        "Modèle d'e-mail. Un objet ou un message absent, null ou vide reprend le texte par défaut"
            + " de l'application.")
public record BillingEmailTemplateRequest(
    @Schema(
            description =
                "Objet, 200 caractères au plus, sur une seule ligne (INVALID_VALUE sinon).",
            maxLength = 200)
        @Size(max = 200)
        String subject,
    @Schema(description = "Message, 5 000 caractères au plus.", maxLength = 5000) @Size(max = 5000)
        String body) {}
