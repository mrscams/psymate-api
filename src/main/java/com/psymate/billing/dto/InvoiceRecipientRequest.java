package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Destinataire d'un brouillon (PATCH {@code recipient}, L5.3). */
@Schema(
    description =
        "Destinataire du brouillon : remplace nom et adresse. Tout autre champ est refusé"
            + " (UNKNOWN_FIELD).")
public record InvoiceRecipientRequest(
    @Schema(
            description = "Nom, obligatoire, 200 caractères au plus.",
            maxLength = 200,
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 200)
        String name,
    @Schema(
            description = "Adresse, 300 caractères au plus ; null ou vide l'efface.",
            maxLength = 300)
        @Size(max = 300)
        String addressLines) {}
