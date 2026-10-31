package com.psymate.clinical.web;

import jakarta.validation.ConstraintViolation;

/**
 * Une erreur par champ : {@code field} est le chemin JSON du champ (occurred.from,
 * feeling.intensity, tags[2]), {@code code} un code stable pour le client, le message (localisé)
 * est ajouté à la sérialisation.
 */
record FieldProblem(String field, String code) {

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
