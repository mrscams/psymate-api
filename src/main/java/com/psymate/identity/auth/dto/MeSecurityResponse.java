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
                "Dernière connexion réussie, y compris celle qui a ouvert la session courante si"
                    + " c'est la plus récente. null si le compte n'a jamais ouvert de session par"
                    + " POST /api/auth/login (ouverture par confirmation d'e-mail seulement).")
        LastLoginResponse lastLogin) {}
