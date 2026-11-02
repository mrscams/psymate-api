package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Résumé rédigé par règles (jusqu'à 5 phrases, chacune omise si ses données manquent,"
            + " sans IA), dans la langue de la requête (Accept-Language : français par défaut,"
            + " anglais si demandé).")
public record NarrativeDto(
    @Schema(
            description =
                "Phrases dans l'ordre : cadre du suivi, tendance, situations ouvertes, événement"
                    + " non commenté, traitement en cours ; chacune est omise si ses données"
                    + " manquent (liste éventuellement vide).")
        List<String> sentences,
    @Schema(
            description =
                "Point d'attention issu des drapeaux actifs ; null sans drapeau. Le mot « risque »"
                    + " n'apparaît jamais.",
            example = "À surveiller depuis le 14 mars 2026 (motif : Troubles du sommeil).")
        String attention) {}
