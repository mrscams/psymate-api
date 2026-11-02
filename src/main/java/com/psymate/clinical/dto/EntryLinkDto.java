package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Lien posé à la création, dans son sens réel (source vers cible). {@code label} est le verbe vu
 * depuis la nouvelle entrée : libellé inverse si elle est la cible d'un type dirigé ; anglais si
 * Accept-Language: en, français sinon.
 */
@Schema(
    description =
        "Lien accepté posé avec une nouvelle entrée, dans son sens réel (source vers cible).")
public record EntryLinkDto(
    @Schema(description = "Identifiant du lien.") UUID id,
    @Schema(description = "Entrée source du lien.") UUID sourceId,
    @Schema(description = "Entrée cible du lien.") UUID targetId,
    @Schema(description = "Code du type de lien.", example = "triggers") String linkType,
    @Schema(
            description =
                "Verbe du lien vu depuis la nouvelle entrée : libellé inverse si elle est la"
                    + " cible d'un type dirigé. En anglais si Accept-Language vaut en, en"
                    + " français sinon.",
            example = "déclenche")
        String label) {}
