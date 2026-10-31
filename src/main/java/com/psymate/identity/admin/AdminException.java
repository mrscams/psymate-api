package com.psymate.identity.admin;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Erreur métier de l'administration, rendue en RFC 9457 par AdminExceptionHandler. */
class AdminException extends RuntimeException {

  /** Erreur de champ : champ, code, clé de message. */
  record FieldError(String field, String code, String messageKey) {}

  private final HttpStatus status;
  private final String code;
  private final String messageKey;
  private final List<FieldError> errors;
  private final Map<String, Object> extra;

  AdminException(
      HttpStatus status,
      String code,
      String messageKey,
      List<FieldError> errors,
      Map<String, Object> extra) {
    super(code);
    this.status = status;
    this.code = code;
    this.messageKey = messageKey;
    this.errors = errors;
    this.extra = extra;
  }

  static AdminException of(HttpStatus status, String code, String messageKey) {
    return new AdminException(status, code, messageKey, List.of(), Map.of());
  }

  static AdminException fields(FieldError... errors) {
    return new AdminException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "VALIDATION_FAILED",
        "admin.error.invalid",
        List.of(errors),
        Map.of());
  }

  static AdminException with(
      HttpStatus status, String code, String messageKey, Map<String, Object> extra) {
    return new AdminException(status, code, messageKey, List.of(), extra);
  }

  HttpStatus status() {
    return status;
  }

  String code() {
    return code;
  }

  String messageKey() {
    return messageKey;
  }

  List<FieldError> errors() {
    return errors;
  }

  Map<String, Object> extra() {
    return extra;
  }
}
