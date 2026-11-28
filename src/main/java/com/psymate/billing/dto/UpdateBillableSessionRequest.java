package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Tarif ou prestation d'une seule séance à facturer (PATCH, L5.2). Un champ absent reste inchangé ;
 * un null explicite l'efface et rend le tarif calculé (G Q87). C'est pourquoi la requête n'est pas
 * un record : chaque setter retient que la clé était présente dans le JSON (comme {@link
 * UpdateBillingServiceRequest}).
 */
@Schema(
    description =
        "Tarif ou prestation de cette séance seulement. Un champ absent reste inchangé ; null"
            + " l'efface et rend le tarif calculé. Montant en centimes d'euro entiers : un nombre"
            + " décimal ou une chaîne est refusé (INVALID_FORMAT). Tout autre champ est refusé"
            + " (UNKNOWN_FIELD).")
public final class UpdateBillableSessionRequest {

  @Schema(
      description =
          "Montant fixé sur la séance, en centimes d'euro, entier strictement positif ; null rend"
              + " le tarif calculé (tarif du patient, sinon prix de la prestation).",
      types = {"integer", "null"},
      format = "int32",
      minimum = "1",
      example = "7000")
  @Positive
  private Integer amount;

  private boolean amountPresent;

  @Schema(
      description =
          "Prestation de la séance, non archivée, du praticien ; null revient à la prestation par"
              + " défaut.",
      types = {"string", "null"},
      format = "uuid")
  private UUID serviceId;

  private boolean serviceIdPresent;

  public UpdateBillableSessionRequest() {}

  public Integer getAmount() {
    return amount;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setAmount(Integer amount) {
    this.amount = amount;
    this.amountPresent = true;
  }

  public UUID getServiceId() {
    return serviceId;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setServiceId(UUID serviceId) {
    this.serviceId = serviceId;
    this.serviceIdPresent = true;
  }

  /** La clé amount figurait dans le corps (valeur éventuellement null). */
  public boolean amountProvided() {
    return amountPresent;
  }

  /** La clé serviceId figurait dans le corps (valeur éventuellement null). */
  public boolean serviceIdProvided() {
    return serviceIdPresent;
  }
}
