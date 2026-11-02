package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Corps identique que le compte existe déjà ou non (anti-énumération). */
@Schema(
    description =
        "Réponse de l'inscription, identique que l'adresse e-mail soit déjà inscrite ou non"
            + " (anti-énumération) : un e-mail de confirmation ou un e-mail « compte existant »"
            + " part dans les deux cas.")
public record RegisterResponse(
    @Schema(
            description = "Toujours confirmation_email_sent.",
            allowableValues = {"confirmation_email_sent"},
            example = "confirmation_email_sent")
        String status) {}
