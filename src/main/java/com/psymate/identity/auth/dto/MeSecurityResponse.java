package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Sécurité du compte : sessions actives et dernière connexion.")
public record MeSecurityResponse(
    @Schema(
            description =
                "Sessions non expirées du praticien (les siennes uniquement), la plus récemment"
                    + " utilisée d'abord. Contient toujours la session courante.")
        List<ActiveSessionResponse> sessions,
    @Schema(
            description =
                "Connexion précédente : la dernière connexion réussie (POST /api/auth/login)"
                    + " antérieure à l'ouverture de la session courante, sans compter celle-ci. null"
                    + " à la toute première connexion du compte (une session ouverte par"
                    + " confirmation d'e-mail n'est pas une connexion et lit la dernière connexion"
                    + " antérieure) ou pour une session ouverte avant cette fonction.")
        LastLoginResponse lastLogin) {}
