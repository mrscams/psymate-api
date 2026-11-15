package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Personne qui n'est pas encore patiente (G Q60) : nom obligatoire, prénom et téléphone
 * facultatifs, sans fiche patient. Le message {@code INVALID_FORMAT} du téléphone est le code de
 * champ renvoyé (lu par {@code AgendaExceptionHandler}).
 */
@Schema(
    description =
        "Invité : personne qui n'a pas encore de fiche patient (G Q60). Nom obligatoire, prénom et"
            + " téléphone facultatifs. Ses données ne vivent que dans le rendez-vous, avec les"
            + " mêmes règles d'accès qu'un patient, et sont purgées 6 mois après la fin (ou"
            + " l'annulation) du rendez-vous.")
public record GuestRequest(
    @Schema(description = "Prénom, 100 caractères au plus.", maxLength = 100, example = "Sara")
        @Size(max = 100)
        String firstName,
    @Schema(
            description = "Nom, 100 caractères au plus. Obligatoire.",
            maxLength = 100,
            example = "Benali")
        @NotBlank
        @Size(max = 100)
        String lastName,
    @Schema(
            description =
                "Téléphone, 30 caractères au plus : chiffres, espaces, +, point, tiret,"
                    + " parenthèses, au moins un chiffre (INVALID_FORMAT sinon) ; chaîne vide :"
                    + " aucun téléphone.",
            maxLength = 30,
            example = "+33 6 12 34 56 78")
        @Size(max = 30)
        @Pattern(regexp = GuestRequest.PHONE, message = GuestRequest.INVALID_FORMAT)
        String phone) {

  /** Chiffres, espaces, +, point, tiret, parenthèses, au moins un chiffre ; ou vide (aucun). */
  public static final String PHONE = "([0-9 +.()-]*[0-9][0-9 +.()-]*)?";

  /** Code de champ porté par le message de la contrainte du téléphone. */
  public static final String INVALID_FORMAT = "INVALID_FORMAT";
}
