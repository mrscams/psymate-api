package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Comptes de toute la fenêtre filtrée (pas seulement la page), clés = code ou libellé. */
@Schema(
    description =
        "Comptes de toute la fenêtre filtrée (et non de la seule page), indépendants du curseur."
            + " Triés du plus fréquent au plus rare.")
public record TimelineFacetsDto(
    @Schema(description = "Nombre d'entrées par type (clé = code du type).")
        Map<String, Integer> types,
    @Schema(description = "Nombre de ressentis par émotion (clé = code de l'émotion).")
        Map<String, Integer> emotions,
    @Schema(description = "Nombre d'entrées par tag (clé = libellé du tag).")
        Map<String, Integer> tags) {}
