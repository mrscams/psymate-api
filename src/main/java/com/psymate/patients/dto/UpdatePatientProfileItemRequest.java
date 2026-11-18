package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Correction partielle d'une information du Profil 360 (J3). Une clé absente laisse le champ
 * inchangé ; un null explicite efface detail, periodLabel ou periodYear. C'est pourquoi la requête
 * n'est pas un record : chaque setter retient que la clé figurait dans le JSON. Les espaces de bord
 * sont retirés à la lecture ; un détail ou une période vides valent null.
 */
@Schema(
    description =
        "Correction partielle : une clé absente reste inchangée ; null efface detail, periodLabel"
            + " ou periodYear ; title ne peut pas devenir vide. La rubrique peut changer : en"
            + " quittant history, la période est effacée par le serveur ; une période envoyée"
            + " avec une rubrique autre que history est refusée (422 INVALID_VALUE). Toute autre"
            + " clé est refusée (422 UNKNOWN_FIELD).")
public final class UpdatePatientProfileItemRequest {

  @Schema(
      description =
          "Nouvelle rubrique : history, family, health ou values_goals (422 INVALID_VALUE sinon ;"
              + " null : 422 REQUIRED).",
      allowableValues = {"history", "family", "health", "values_goals"},
      example = "family")
  @Pattern(regexp = "history|family|health|values_goals")
  private String category;

  private boolean categoryPresent;

  @Schema(
      description =
          "Nouveau texte de l'information : non vide (422 REQUIRED), 200 caractères au plus après"
              + " retrait des espaces de bord (422 OUT_OF_RANGE).",
      maxLength = 200,
      example = "A grandi à Nantes, aînée de trois enfants")
  @Size(max = 200)
  private String title;

  private boolean titlePresent;

  @Schema(
      description = "Nouveau détail, 2 000 caractères au plus (422 OUT_OF_RANGE) ; null l'efface.",
      maxLength = 2000,
      example = "Dit en garder « un souvenir de maison pleine »")
  @Size(max = 2000)
  private String detail;

  private boolean detailPresent;

  @Schema(
      description =
          "history seulement : nouvelle période libre, 60 caractères au plus (422 OUT_OF_RANGE) ;"
              + " null l'efface.",
      maxLength = 60,
      example = "Adolescence")
  @Size(max = 60)
  private String periodLabel;

  private boolean periodLabelPresent;

  @Schema(
      description =
          "history seulement : nouvelle année de tri, de 1900 à 2100 (422 OUT_OF_RANGE) ; null"
              + " l'efface.",
      minimum = "1900",
      maximum = "2100",
      example = "2004")
  @Min(1900)
  @Max(2100)
  private Integer periodYear;

  private boolean periodYearPresent;

  public UpdatePatientProfileItemRequest() {}

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  public String getCategory() {
    return category;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setCategory(String category) {
    this.category = blankToNull(category);
    this.categoryPresent = true;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title == null ? null : title.strip();
    this.titlePresent = true;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = blankToNull(detail);
    this.detailPresent = true;
  }

  public String getPeriodLabel() {
    return periodLabel;
  }

  public void setPeriodLabel(String periodLabel) {
    this.periodLabel = blankToNull(periodLabel);
    this.periodLabelPresent = true;
  }

  public Integer getPeriodYear() {
    return periodYear;
  }

  public void setPeriodYear(Integer periodYear) {
    this.periodYear = periodYear;
    this.periodYearPresent = true;
  }

  /** La clé category figurait dans le corps (valeur éventuellement null). */
  public boolean categoryProvided() {
    return categoryPresent;
  }

  /** La clé title figurait dans le corps (valeur éventuellement null). */
  public boolean titleProvided() {
    return titlePresent;
  }

  /** La clé detail figurait dans le corps (valeur éventuellement null). */
  public boolean detailProvided() {
    return detailPresent;
  }

  /** La clé periodLabel figurait dans le corps (valeur éventuellement null). */
  public boolean periodLabelProvided() {
    return periodLabelPresent;
  }

  /** La clé periodYear figurait dans le corps (valeur éventuellement null). */
  public boolean periodYearProvided() {
    return periodYearPresent;
  }
}
