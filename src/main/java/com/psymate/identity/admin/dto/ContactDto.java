package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Coordonnées professionnelles du praticien, vues par l'administration.")
public record ContactDto(
    @Schema(
            description = "Téléphone professionnel ; null si non renseigné.",
            example = "+33 4 72 00 00 00")
        String phone,
    @Schema(
            description =
                "Numéro professionnel (RPPS, ADELI ou équivalent) ; null si non renseigné.")
        String professionalNumber,
    @Schema(description = "Adresse du cabinet ; ses champs sont null si non renseignés.")
        AddressDto address,
    @Schema(description = "Vrai si le praticien exerce uniquement à distance.")
        boolean remoteOnly) {}
