package com.psymate.linking.web;

/**
 * Une erreur par champ : {@code field} est le chemin JSON du champ, {@code code} un code stable.
 */
record FieldProblem(String field, String code) {

  /** Code stable à partir du nom de la contrainte Jakarta (NotBlank, Size, Pattern...). */
  static String codeOf(String constraint) {
    return switch (constraint) {
      case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
      case "Min", "Max", "Size" -> "OUT_OF_RANGE";
      case "Pattern" -> "INVALID_VALUE";
      default -> "INVALID";
    };
  }
}
