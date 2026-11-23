package com.psymate.vocabulary.api;

import com.psymate.shared.i18n.ServedLanguage;
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
                "Libellé arabe vu depuis l'entrée source ; null s'il n'y en a pas : afficher alors"
                    + " labelFr.",
            example = "يُثير")
        String labelAr,
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
                "Libellé arabe vu depuis l'entrée cible ; null si non orienté ou s'il n'y en a"
                    + " pas : afficher alors inverseLabelFr.",
            example = "يُثيره")
        String inverseLabelAr,
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
        int sortOrder) {

  /** Type sans libellé arabe (antérieur à V17) : {@code labelAr} et {@code inverseLabelAr} nuls. */
  public LinkTypeView(
      String code,
      boolean directed,
      String labelFr,
      String labelEn,
      String inverseLabelFr,
      String inverseLabelEn,
      List<String> allowedSourceTypes,
      List<String> allowedTargetTypes,
      String lineStyle,
      int sortOrder) {
    this(
        code,
        directed,
        labelFr,
        labelEn,
        null,
        inverseLabelFr,
        inverseLabelEn,
        null,
        allowedSourceTypes,
        allowedTargetTypes,
        lineStyle,
        sortOrder);
  }

  /**
   * Libellé vu depuis la source dans la langue servie (G Q98) : l'anglais en anglais, l'arabe en
   * arabe s'il existe et le français sinon, le français en français.
   */
  public String label(ServedLanguage language) {
    return switch (language) {
      case EN -> labelEn;
      case AR -> orFrench(labelAr, labelFr);
      case FR -> labelFr;
    };
  }

  /**
   * Libellé vu depuis la cible dans la langue servie, même règle que {@link #label} ; null quand le
   * type n'en a pas (non orienté).
   */
  public String inverseLabel(ServedLanguage language) {
    return switch (language) {
      case EN -> inverseLabelEn;
      case AR -> orFrench(inverseLabelAr, inverseLabelFr);
      case FR -> inverseLabelFr;
    };
  }

  private static String orFrench(String arabic, String french) {
    return arabic == null || arabic.isBlank() ? french : arabic;
  }
}
