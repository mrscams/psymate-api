package com.psymate.directory;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Erreur métier de l'administration du vocabulaire de l'annuaire (M1-14b), rendue en RFC 9457 par
 * DirectoryVocabularyAdminExceptionHandler avec un message dans la langue de l'appelant.
 */
class VocabularyException extends RuntimeException {

  /** Erreur de champ : champ, code, clé de message. */
  record FieldError(String field, String code, String messageKey) {}

  private final HttpStatus status;
  private final String code;
  private final String messageKey;
  private final List<FieldError> errors;
  private final Map<String, Object> extra;

  private VocabularyException(
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

  static VocabularyException of(HttpStatus status, String code, String messageKey) {
    return new VocabularyException(status, code, messageKey, List.of(), Map.of());
  }

  static VocabularyException with(
      HttpStatus status, String code, String messageKey, Map<String, Object> extra) {
    return new VocabularyException(status, code, messageKey, List.of(), extra);
  }

  static VocabularyException invalid(List<FieldError> errors) {
    return new VocabularyException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "VALIDATION_FAILED",
        "vocabulary.error.invalid",
        errors,
        Map.of());
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
