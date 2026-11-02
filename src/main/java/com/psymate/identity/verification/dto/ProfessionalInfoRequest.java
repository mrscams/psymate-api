package com.psymate.identity.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Champs absents ou null : inchangés ; chaîne vide : champ effacé. */
@Schema(
    description =
        "Mise à jour partielle des informations professionnelles : un champ absent ou null est"
            + " inchangé, une chaîne vide efface le champ, toute autre valeur est contrôlée.")
public record ProfessionalInfoRequest(
    @Schema(
            description =
                "Numéro professionnel : 4 à 20 caractères parmi lettres, chiffres, espace et"
                    + " tiret. Vide : effacé.",
            example = "759912345")
        String professionalNumber,
    @Schema(
            description =
                "Téléphone : 6 à 30 caractères parmi chiffres, + espace ( ) . et tiret. Vide :"
                    + " effacé.",
            example = "+33 6 12 34 56 78")
        String phone,
    @Schema(
            description =
                "Adresse du cabinet, champ par champ (200 caractères au maximum chacun). Absente"
                    + " : adresse inchangée.")
        AddressDto address,
    @Schema(description = "true pour exercer uniquement à distance. Absent ou null : inchangé.")
        Boolean remoteOnly) {}
