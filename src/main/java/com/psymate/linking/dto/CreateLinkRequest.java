package com.psymate.linking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Corps de POST /api/entries/{id}/links. {@code direction} dit où se place l'entrée de l'URL :
 * outgoing (défaut), elle est la source et {@code targetId} la cible ; incoming, {@code targetId}
 * est la source et l'entrée de l'URL la cible. La provenance et le statut ne s'écrivent jamais : en
 * V1 un lien posé à la main est toujours manual et accepted.
 */
@Schema(
    description =
        "Lien à poser entre l'entrée de l'URL et une autre entrée du même patient. La provenance"
            + " (manual) et le statut (accepted) ne s'envoient pas.")
public record CreateLinkRequest(
    @Schema(
            description =
                "Identifiant de l'autre entrée à lier ; elle doit être du même patient, non"
                    + " archivée et différente de l'entrée de l'URL.",
            example = "c1a7e9d4-2b3f-4a6c-8d5e-0f1a2b3c4d5e")
        @NotNull
        UUID targetId,
    @Schema(
            description =
                "Code du type de lien, parmi ceux de GET /api/vocabulary/link-types (par exemple"
                    + " triggers, caused_by, worsens, relieves, co_occurs, follows, part_of,"
                    + " treats). Le type doit être autorisé pour le couple (type de l'entrée"
                    + " source, type de l'entrée cible).",
            example = "triggers")
        @NotBlank
        String linkType,
    @Schema(
            description =
                "Place de l'entrée de l'URL dans le lien : outgoing = elle est la source et"
                    + " targetId la cible ; incoming = targetId est la source et elle est la"
                    + " cible. Défaut : outgoing.",
            allowableValues = {"outgoing", "incoming"},
            defaultValue = "outgoing")
        @Pattern(regexp = "incoming|outgoing")
        String direction,
    @Schema(
            description =
                "Justification libre du lien, 500 caractères au plus ; espaces de début et de"
                    + " fin retirés, vide traité comme absent. Optionnelle.",
            maxLength = 500,
            example = "Depuis le décès, les réveils sont angoissés.")
        @Size(max = 500)
        String rationale) {}
