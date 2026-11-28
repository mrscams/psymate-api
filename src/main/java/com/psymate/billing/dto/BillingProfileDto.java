package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Profil de facturation d'un patient (L5.1). Tarif en centimes d'euro entiers (G Q84). */
@Schema(
    description =
        "Profil de facturation d'un patient. Tous les champs sont null tant que rien n'est"
            + " enregistré.")
public record BillingProfileDto(
    @Schema(
            description =
                "Tarif du patient en centimes d'euro (entier strictement positif) : 5500 ="
                    + " 55,00 €. Null : le tarif vient de la prestation par défaut (G Q87).",
            minimum = "1",
            example = "5500")
        Integer rate,
    @Schema(
            description = "Adresse de facturation, lignes séparées par des sauts de ligne.",
            maxLength = 300)
        String addressLines,
    @Schema(description = "Nom du tiers à qui adresser la facture.", maxLength = 200)
        String thirdPartyName,
    @Schema(description = "Adresse du tiers.", maxLength = 300) String thirdPartyAddress) {

  /**
   * Profil d'un patient sans ligne {@code billing_patient} : tous les champs à null. Hors du mapper
   * : MapStruct prendrait une méthode sans paramètre qui rend ce type pour une fabrique d'objets.
   */
  public static BillingProfileDto empty() {
    return new BillingProfileDto(null, null, null, null);
  }
}
