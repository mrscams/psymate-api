package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Personne qui n'est pas encore patiente (G Q60) : nom obligatoire, prénom, téléphone et e-mail
 * (V15, M14-04) facultatifs, sans fiche patient. Le message {@code INVALID_FORMAT} du téléphone et
 * de l'e-mail est le code de champ renvoyé (lu par {@code AgendaExceptionHandler}).
 */
@Schema(
    description =
        "Invité : personne qui n'a pas encore de fiche patient (G Q60). Nom obligatoire, prénom,"
            + " téléphone et e-mail facultatifs. Ses données ne vivent que dans le rendez-vous,"
            + " avec les mêmes règles d'accès qu'un patient, et sont purgées 6 mois après la fin"
            + " (ou l'annulation) du rendez-vous.")
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
        String phone,
    @Schema(
            description =
                "E-mail, 320 caractères au plus (OUT_OF_RANGE sinon) : une seule @, au moins un"
                    + " point après elle, aucun espace (INVALID_FORMAT sinon) ; chaîne vide :"
                    + " aucun e-mail.",
            maxLength = 320,
            example = "sara.benali@example.test")
        @Size(max = 320)
        @Pattern(regexp = GuestRequest.EMAIL, message = GuestRequest.INVALID_FORMAT)
        String email) {

  /** Chiffres, espaces, +, point, tiret, parenthèses, au moins un chiffre ; ou vide (aucun). */
  public static final String PHONE = "([0-9 +.()-]*[0-9][0-9 +.()-]*)?";

  /**
   * Partie locale, une seule @, domaine avec au moins un point, sans espace ; ou vide (aucun). Même
   * règle que l'adresse de l'administrateur initial : ce n'est qu'un garde-fou de saisie, l'adresse
   * n'est jamais vérifiée ici.
   */
  public static final String EMAIL = "([^@\\s]+@[^@\\s]+\\.[^@\\s]+)?";

  /** Code de champ porté par le message des contraintes du téléphone et de l'e-mail. */
  public static final String INVALID_FORMAT = "INVALID_FORMAT";
}
