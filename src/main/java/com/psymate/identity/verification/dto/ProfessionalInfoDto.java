package com.psymate.identity.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Informations professionnelles du praticien, telles qu'enregistrées.")
public record ProfessionalInfoDto(
    @Schema(
            description = "Numéro professionnel (ADELI, RPPS ou autre) ; null si non renseigné.",
            example = "759912345")
        String professionalNumber,
    @Schema(
            description = "Téléphone, tel que saisi ; null si non renseigné.",
            example = "+33612345678")
        String phone,
    @Schema(description = "Adresse du cabinet ; ses champs valent null si absents.")
        AddressDto address,
    @Schema(
            description =
                "true si le praticien exerce uniquement à distance (visio) : l'adresse n'est pas"
                    + " exigée.")
        boolean remoteOnly) {}
