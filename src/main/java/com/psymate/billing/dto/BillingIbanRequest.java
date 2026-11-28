package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Enregistrement de l'IBAN (PUT, L5.1, G Q95) : l'IBAN et le mot de passe du compte. Ni l'un ni
 * l'autre ne sort de la requête : {@link #toString} les masque.
 */
@Schema(
    description =
        "IBAN à enregistrer et mot de passe actuel du compte. L'IBAN est rangé chiffré, jamais"
            + " renvoyé ; seuls ses quatre derniers caractères restent lisibles (ibanLast4).")
public record BillingIbanRequest(
    @Schema(
            description =
                "IBAN, espaces admis, lettres en majuscules ou minuscules ; format et clé mod 97"
                    + " vérifiés (IBAN_INVALID).",
            example = "FR76 3000 6000 0112 3456 7890 189")
        @NotBlank
        String iban,
    @Schema(description = "Mot de passe actuel du compte (403 PASSWORD_INVALID s'il est faux).")
        @NotBlank
        String currentPassword) {

  @Override
  public String toString() {
    return "BillingIbanRequest[iban=***, currentPassword=***]";
  }
}
