package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Ajout d'une information au Profil 360 (J3). Les espaces de bord sont retirés à la lecture du
 * corps, avant la validation : les longueurs se comptent après ce retrait ; une rubrique, un détail
 * ou une période vides valent null.
 */
@Schema(
    description =
        "Ajout d'une information au Profil 360. La période (periodLabel, periodYear) n'est admise"
            + " que dans la rubrique history (422 INVALID_VALUE sinon). Les espaces de bord sont"
            + " retirés ; toute autre clé est refusée (422 UNKNOWN_FIELD).")
public record CreatePatientProfileItemRequest(
    @Schema(
            description =
                "Rubrique : history = Histoire et souvenirs, family = Famille et entourage,"
                    + " health = Santé et antécédents, values_goals = Valeurs et projets."
                    + " Obligatoire (422 REQUIRED) ; autre valeur : 422 INVALID_VALUE.",
            allowableValues = {"history", "family", "health", "values_goals"},
            example = "history")
        @NotNull
        @Pattern(regexp = "history|family|health|values_goals")
        String category,
    @Schema(
            description =
                "L'information elle-même : obligatoire, non vide (422 REQUIRED), 200 caractères"
                    + " au plus après retrait des espaces de bord (422 OUT_OF_RANGE).",
            maxLength = 200,
            example = "A grandi à Nantes, aînée de trois enfants")
        @NotBlank
        @Size(max = 200)
        String title,
    @Schema(
            description =
                "Seconde ligne facultative (précision, citation), 2 000 caractères au plus (422"
                    + " OUT_OF_RANGE).",
            maxLength = 2000,
            example = "Dit en garder « un souvenir de maison pleine »")
        @Size(max = 2000)
        String detail,
    @Schema(
            description =
                "history seulement : période libre, affichée telle quelle (« Enfance », « Vers"
                    + " 2000 », « 2002 – 2009 »), 60 caractères au plus (422 OUT_OF_RANGE).",
            maxLength = 60,
            example = "Enfance")
        @Size(max = 60)
        String periodLabel,
    @Schema(
            description =
                "history seulement : année qui ne sert qu'à trier le fil chronologique, de 1900 à"
                    + " 2100 (422 OUT_OF_RANGE).",
            minimum = "1900",
            maximum = "2100",
            example = "1998")
        @Min(1900)
        @Max(2100)
        Integer periodYear) {

  public CreatePatientProfileItemRequest {
    category = blankToNull(category);
    title = title == null ? null : title.strip();
    detail = blankToNull(detail);
    periodLabel = blankToNull(periodLabel);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }
}
