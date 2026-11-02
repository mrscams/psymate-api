package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Lien demandé avec une nouvelle entrée. {@code direction} : outgoing (défaut, la nouvelle entrée
 * est la source) ou incoming (elle est la cible).
 */
@Schema(
    description =
        "Lien à poser avec une nouvelle entrée, accepté en même temps qu'elle (tout ou rien).")
public record EntryLinkRequest(
    @Schema(description = "Entrée à lier. Obligatoire (REQUIRED).") UUID targetId,
    @Schema(
            description =
                "Code du type de lien. Obligatoire (REQUIRED). Inconnu : UNKNOWN_LINK_TYPE ; non"
                    + " permis pour ce couple d'entrées : LINK_TYPE_NOT_ALLOWED (la réponse"
                    + " porte alors allowedLinkTypes).",
            example = "triggers")
        String linkType,
    @Schema(
            description =
                "Sens vu depuis la nouvelle entrée : outgoing (défaut) = elle est la source ;"
                    + " incoming = elle est la cible. Autre valeur : INVALID_VALUE.",
            allowableValues = {"outgoing", "incoming"},
            defaultValue = "outgoing",
            example = "outgoing")
        String direction) {}
