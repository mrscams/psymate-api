package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Modèle d'un e-mail adressé au patient (L5.1) : objet et message. */
@Schema(
    description =
        "Modèle d'e-mail : objet et message. Variables remplacées à l'envoi : {numéro}, {montant},"
            + " {reste dû}, {échéance}.")
public record BillingEmailTemplateDto(
    @Schema(description = "Objet, 200 caractères au plus.", example = "Facture {numéro}")
        String subject,
    @Schema(description = "Message, 5 000 caractères au plus.") String body) {}
