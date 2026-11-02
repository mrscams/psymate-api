package com.psymate.directory.model;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Erreur métier de l'administration du vocabulaire de l'annuaire (M1-14b), rendue en RFC 9457 par
 * DirectoryVocabularyAdminExceptionHandler avec un message dans la langue de l'appelant.
 */
public class DirectoryVocabularyException extends RuntimeException {

  /** Erreur de champ : champ, code, clé de message. */
  public record FieldError(String field, String code, String messageKey) {}

  private final HttpStatus status;
  private final String code;
  private final String messageKey;
  private final List<FieldError> errors;
  private final Map<String, Object> extra;

  private DirectoryVocabularyException(
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

  public static DirectoryVocabularyException of(HttpStatus status, String code, String messageKey) {
    return new DirectoryVocabularyException(status, code, messageKey, List.of(), Map.of());
  }

  public static DirectoryVocabularyException with(
      HttpStatus status, String code, String messageKey, Map<String, Object> extra) {
    return new DirectoryVocabularyException(status, code, messageKey, List.of(), extra);
  }

  public static DirectoryVocabularyException invalid(List<FieldError> errors) {
    return new DirectoryVocabularyException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "VALIDATION_FAILED",
        "vocabulary.error.invalid",
        errors,
        Map.of());
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public String messageKey() {
    return messageKey;
  }

  public List<FieldError> errors() {
    return errors;
  }

  public Map<String, Object> extra() {
    return extra;
  }
}
