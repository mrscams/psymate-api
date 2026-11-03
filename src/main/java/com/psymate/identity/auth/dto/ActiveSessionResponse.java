package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Une session active du praticien (un appareil connecté).")
public record ActiveSessionResponse(
    @Schema(
            description =
                "Identifiant opaque de la session, à passer à POST /api/me/sessions/{id}/revoke."
                    + " Ce n'est pas l'identifiant du cookie de session et il ne permet pas de"
                    + " s'authentifier.")
        UUID id,
    @Schema(description = "Ouverture de la session (UTC).") Instant createdAt,
    @Schema(description = "Dernier appel authentifié de la session (UTC).") Instant lastAccessAt,
    @Schema(
            description =
                "Expiration de la session si elle n'est plus utilisée (UTC) : 12 h après le dernier"
                    + " appel, 30 jours avec « rester connecté » (G Q6). Chaque appel la repousse.")
        Instant expiresAt,
    @Schema(description = "Vrai pour la session qui porte cet appel ; une seule par réponse.")
        boolean current,
    @Schema(
            description =
                "Appareil déduit de l'User-Agent à la connexion : navigateur et système,"
                    + " par exemple « Chrome · Windows » (jamais l'en-tête brut). null pour une"
                    + " session ouverte avant cette fonction ou dont l'en-tête n'est pas reconnu.",
            example = "Chrome · Windows")
        String device,
    @Schema(
            description =
                "Ville déduite de l'adresse IP. Non renseignée en V1 : aucune géolocalisation,"
                    + " toujours null.")
        String city) {}
