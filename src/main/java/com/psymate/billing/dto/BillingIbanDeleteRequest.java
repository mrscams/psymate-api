package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Effacement de l'IBAN (DELETE, L5.1, G Q95) : mot de passe du compte, masqué par toString. */
@Schema(description = "Mot de passe actuel du compte, exigé pour effacer l'IBAN.")
public record BillingIbanDeleteRequest(
    @Schema(description = "Mot de passe actuel du compte (403 PASSWORD_INVALID s'il est faux).")
        @NotBlank
        String currentPassword) {

  @Override
  public String toString() {
    return "BillingIbanDeleteRequest[currentPassword=***]";
  }
}
