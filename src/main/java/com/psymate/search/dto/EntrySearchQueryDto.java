package com.psymate.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Requête telle que le serveur l'a interprétée à partir du paramètre q.")
public record EntrySearchQueryDto(
    @Schema(
            description =
                "Texte libre restant après retrait des jetons (tag:, émotion:, avant:, après:) ;"
                    + " chaîne vide s'il n'en reste pas (recherche sans texte).",
            example = "licenciement")
        String text,
    @Schema(description = "Valeurs des jetons tag: de q (hors paramètre tags).") List<String> tags,
    @Schema(description = "Valeurs des jetons émotion: / emotion: de q (hors paramètre emotions).")
        List<String> emotions,
    @Schema(
            description =
                "Borne exclusive déduite de avant: / before: (début de la période, UTC ISO 8601) ;"
                    + " null sans jeton.",
            example = "2026-12-01T00:00:00Z")
        Instant before,
    @Schema(
            description =
                "Borne inclusive déduite de après: / after: (premier instant suivant la période,"
                    + " UTC ISO 8601) ; null sans jeton.",
            example = "2026-07-01T00:00:00Z")
        Instant after) {}
