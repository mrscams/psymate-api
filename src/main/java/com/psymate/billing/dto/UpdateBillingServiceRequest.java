package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Modification partielle d'une prestation (PATCH, L5.1). Un champ absent ou null reste inchangé,
 * sauf durationMin : un null explicite efface la durée. C'est pourquoi la requête n'est pas un
 * record : le setter de durationMin retient que la clé était présente dans le JSON (comme {@code
 * UpdateAppointmentRequest.planningNote} de l'agenda).
 */
@Schema(
    description =
        "Modification partielle : un champ absent ou null reste inchangé ; durationMin à null"
            + " efface la durée ; isDefault à true fait de cette prestation la prestation par"
            + " défaut et retire le défaut de l'autre. Montant en centimes d'euro entiers : un"
            + " nombre décimal est refusé (INVALID_FORMAT). Tout autre champ est refusé"
            + " (UNKNOWN_FIELD).")
public final class UpdateBillingServiceRequest {

  @Schema(
      description = "Nouveau libellé, non vide, 120 caractères au plus.",
      maxLength = 120,
      example = "Séance de couple")
  @Size(max = 120)
  private String label;

  @Schema(
      description = "Nouvelle durée en minutes, de 1 à 600 ; null l'efface.",
      types = {"integer", "null"},
      format = "int32",
      minimum = "1",
      maximum = "600",
      example = "50")
  @Min(1)
  @Max(600)
  private Integer durationMin;

  private boolean durationMinPresent;

  @Schema(
      description = "Nouveau prix en centimes d'euro, entier strictement positif.",
      minimum = "1",
      example = "7000")
  @Positive
  private Integer price;

  @Schema(
      description =
          "true : devient la prestation par défaut. false sur la prestation par défaut est refusé"
              + " (INVALID_VALUE) : il faut désigner une autre prestation par défaut.")
  private Boolean isDefault;

  public UpdateBillingServiceRequest() {}

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public Integer getDurationMin() {
    return durationMin;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setDurationMin(Integer durationMin) {
    this.durationMin = durationMin;
    this.durationMinPresent = true;
  }

  public Integer getPrice() {
    return price;
  }

  public void setPrice(Integer price) {
    this.price = price;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }

  /** La clé durationMin figurait dans le corps (valeur éventuellement null). */
  public boolean durationMinProvided() {
    return durationMinPresent;
  }
}
