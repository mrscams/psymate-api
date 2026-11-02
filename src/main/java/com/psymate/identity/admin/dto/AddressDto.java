package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "AdminAddressDto",
    description = "Adresse professionnelle du praticien, telle que saisie dans son profil.")
public record AddressDto(
    @Schema(description = "Numéro et nom de la rue ; null si non renseigné.") String street,
    @Schema(description = "Code postal ; null si non renseigné.") String postalCode,
    @Schema(description = "Ville ; null si non renseignée.") String city) {}
