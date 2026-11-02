package com.psymate.clinical.controller;

import com.psymate.shared.problem.FieldProblem;
import jakarta.validation.ConstraintViolation;

/**
 * Erreurs de champ de clinical ({@link FieldProblem} de shared, sans message : {@link
 * ClinicalExceptionHandler} ajoute le message localisé). {@code field} est le chemin JSON du champ
 * (occurred.from, feeling.intensity, tags[2]) ou le nom du paramètre de requête.
 *
 * <p>Codes de champ produits par clinical : REQUIRED (champ obligatoire manquant ou vide),
 * OUT_OF_RANGE (hors bornes), INVALID_VALUE (valeur hors liste admise), INVALID_FORMAT (date, UUID
 * ou nombre illisible), INVALID_TIME (dates incohérentes), INVALID (autre contrainte),
 * UNKNOWN_EMOTION, UNKNOWN_TAG, UNKNOWN_FIELD (authorType ou submissionId envoyé),
 * NOT_SUPPORTED_ON_PATCH (links sur un PATCH), DETAIL_NOT_ALLOWED (détail d'un autre type),
 * NOT_ALLOWED_ON_CREATE (champ refusé à la création d'une séance), SESSION_REQUIRED,
 * SESSION_INVALID, SESSION_NOT_ALLOWED, ainsi que les codes de lien ENTRY_NOT_FOUND, SELF_LINK,
 * DIFFERENT_PATIENT, UNKNOWN_LINK_TYPE, LINK_TYPE_NOT_ALLOWED, LINK_EXISTS et ENTRY_ARCHIVED sur
 * links[i].linkType ou links[i].targetId.
 */
final class FieldProblems {

  private FieldProblems() {}

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
