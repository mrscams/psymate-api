package com.psymate.directory;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Erreur métier du module, rendue en RFC 9457 avec un champ {@code code}. */
class ProfileException extends RuntimeException {

  record FieldError(String field, String message) {}

  private final HttpStatus status;
  private final String code;
  private final List<FieldError> errors;
  private final Map<String, Object> extra;

  ProfileException(
      HttpStatus status,
      String code,
      String message,
      List<FieldError> errors,
      Map<String, Object> extra) {
    super(message);
    this.status = status;
    this.code = code;
    this.errors = errors;
    this.extra = extra;
  }

  static ProfileException of(HttpStatus status, String code, String message) {
    return new ProfileException(status, code, message, List.of(), Map.of());
  }

  static ProfileException invalid(List<FieldError> errors) {
    return new ProfileException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "VALIDATION_FAILED",
        "Certains champs sont invalides.",
        errors,
        Map.of());
  }

  HttpStatus status() {
    return status;
  }

  String code() {
    return code;
  }

  List<FieldError> errors() {
    return errors;
  }

  Map<String, Object> extra() {
    return extra;
  }
}
