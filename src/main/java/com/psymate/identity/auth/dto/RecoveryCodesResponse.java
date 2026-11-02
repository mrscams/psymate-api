package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Codes de secours en clair : montrés une seule fois. */
@Schema(description = "Codes de secours de la 2FA, en clair, montrés une seule fois.")
public record RecoveryCodesResponse(
    @Schema(
            description =
                "Dix codes de secours à usage unique, au format XXXXX-XXXXX (majuscules et"
                    + " chiffres). Ils remplacent tous les codes précédents, qui ne"
                    + " fonctionnent plus.",
            example = "K7M2Q-9XWHD")
        java.util.List<String> recoveryCodes) {}
