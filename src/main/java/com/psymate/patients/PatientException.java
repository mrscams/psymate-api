package com.psymate.patients;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Erreur métier du module, rendue en RFC 9457 avec un champ {@code code}. */
class PatientException extends RuntimeException {

  record FieldError(String field, String message) {}

  private final HttpStatus status;
  private final String code;
  private final List<FieldError> errors;

  PatientException(HttpStatus status, String code, String message, List<FieldError> errors) {
    super(message);
    this.status = status;
    this.code = code;
    this.errors = errors;
  }

  static PatientException of(HttpStatus status, String code, String message) {
    return new PatientException(status, code, message, List.of());
  }

  static PatientException invalid(String field, String message) {
    return new PatientException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "VALIDATION_FAILED",
        message,
        List.of(new FieldError(field, message)));
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
}
