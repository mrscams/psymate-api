package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Jeton d'un lien de prise de rendez-vous en ligne (vérification ou gestion, G Q80), toujours dans
 * le corps de la requête, jamais dans une URL d'API.
 */
@Schema(
    description =
        "Jeton reçu par e-mail : lien de vérification (/rendez-vous/confirmer/{jeton}) ou lien de"
            + " gestion (/rendez-vous/gerer/{jeton}). Toujours envoyé dans le corps, jamais dans"
            + " l'URL de l'API.")
public record BookingTokenRequest(
    @Schema(
            description =
                "Jeton tel qu'il figure dans le lien, 200 caractères au plus. Obligatoire.",
            maxLength = 200,
            example = "q9Xw2v0sJb3L5mK8nH1cR4tY7uE6iO0pA2sD5fG8hJk")
        @NotBlank
        @Size(max = 200)
        String token) {

  /** Sans le jeton en clair : il vaut accès au rendez-vous. */
  @Override
  public String toString() {
    return "BookingTokenRequest[token=" + CreateBookingRequest.mask(token) + "]";
  }
}
