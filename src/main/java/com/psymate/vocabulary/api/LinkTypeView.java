package com.psymate.vocabulary.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Type de lien avec sa matrice d'autorisation (codes d'entry_type). */
@Schema(
    description =
        "Type de lien entre deux entrées (huit types), avec la matrice des types d'entrée admis en"
            + " source et en cible.")
public record LinkTypeView(
    @Schema(
            description = "Code stable du type de lien.",
            allowableValues = {
              "triggers",
              "caused_by",
              "worsens",
              "relieves",
              "co_occurs",
              "follows",
              "part_of",
              "treats"
            },
            example = "triggers")
        String code,
    @Schema(
            description =
                "true si le lien a un sens (source vers cible) ; false si symétrique"
                    + " (co_occurs).",
            example = "true")
        boolean directed,
    @Schema(description = "Libellé français vu depuis l'entrée source.", example = "déclenche")
        String labelFr,
    @Schema(description = "Libellé anglais vu depuis l'entrée source.", example = "triggers")
        String labelEn,
    @Schema(
            description =
                "Libellé français vu depuis l'entrée cible ; null si le lien n'est pas orienté.",
            example = "déclenché par")
        String inverseLabelFr,
    @Schema(
            description = "Libellé anglais vu depuis l'entrée cible ; null si non orienté.",
            example = "triggered by")
        String inverseLabelEn,
    @Schema(
            description =
                "Codes des types d'entrée (voir /api/vocabulary/entry-types) admis comme source du"
                    + " lien.")
        List<String> allowedSourceTypes,
    @Schema(description = "Codes des types d'entrée admis comme cible du lien.")
        List<String> allowedTargetTypes,
    @Schema(
            description = "Style de trait conseillé pour le graphe.",
            allowableValues = {"solid", "long_dash", "short_dash", "dotted", "thin", "double"},
            example = "solid")
        String lineStyle,
    @Schema(
            description = "Rang d'affichage, croissant ; la liste est déjà triée dans cet ordre.",
            example = "1")
        int sortOrder) {}
