package com.psymate.clinical.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolation;

/**
 * Une erreur par champ : {@code field} est le chemin JSON du champ (occurred.from,
 * feeling.intensity, tags[2]), {@code code} un code stable pour le client, le message (localisé)
 * est ajouté à la sérialisation.
 */
@Schema(
    description =
        "Erreur sur un champ, telle que le client la reçoit dans errors[] (avec un message"
            + " localisé en plus). Codes de champ produits par clinical : REQUIRED (champ"
            + " obligatoire manquant ou vide), OUT_OF_RANGE (hors bornes), INVALID_VALUE (valeur"
            + " hors liste admise), INVALID_FORMAT (date, UUID ou nombre illisible), INVALID_TIME"
            + " (dates incohérentes), INVALID (autre contrainte), UNKNOWN_EMOTION, UNKNOWN_TAG,"
            + " UNKNOWN_FIELD (authorType ou submissionId envoyé), NOT_SUPPORTED_ON_PATCH (links"
            + " sur un PATCH), DETAIL_NOT_ALLOWED (détail d'un autre type), NOT_ALLOWED_ON_CREATE"
            + " (champ refusé à la création d'une séance), SESSION_REQUIRED, SESSION_INVALID,"
            + " SESSION_NOT_ALLOWED, ainsi que les codes de lien ENTRY_NOT_FOUND, SELF_LINK,"
            + " DIFFERENT_PATIENT, UNKNOWN_LINK_TYPE, LINK_TYPE_NOT_ALLOWED, LINK_EXISTS et"
            + " ENTRY_ARCHIVED sur links[i].linkType ou links[i].targetId.")
record FieldProblem(
    @Schema(
            description = "Chemin JSON du champ fautif, ou nom du paramètre de requête.",
            example = "feeling.intensity")
        String field,
    @Schema(
            description = "Code stable de l'erreur, à tester côté client.",
            example = "OUT_OF_RANGE")
        String code) {

  static FieldProblem required(String field) {
    return new FieldProblem(field, "REQUIRED");
  }

  static FieldProblem invalidValue(String field) {
    return new FieldProblem(field, "INVALID_VALUE");
  }

  /** Violation Jakarta sur un EntryContent : « time » est exposé sous occurred.from. */
  static FieldProblem of(ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath().toString();
    String annotation =
        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    return new FieldProblem("time".equals(path) ? "occurred.from" : path, codeOf(annotation));
  }

  /** Code stable à partir du nom de la contrainte Jakarta (NotBlank, Min, Pattern...). */
  static String codeOf(String constraint) {
    return switch (constraint) {
      case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
      case "Min", "Max", "Size", "Positive", "PositiveOrZero", "Range" -> "OUT_OF_RANGE";
      case "Pattern" -> "INVALID_VALUE";
      default -> "INVALID";
    };
  }
}
