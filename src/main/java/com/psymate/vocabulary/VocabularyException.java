package com.psymate.vocabulary;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Erreur métier du module, rendue en RFC 9457 avec un champ {@code code}. */
public class VocabularyException extends RuntimeException {

  public record FieldError(String field, String message) {}

  private final HttpStatus status;
  private final String code;
  private final List<FieldError> errors;
  private final Map<String, Object> extra;

  public VocabularyException(
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

  public static VocabularyException of(HttpStatus status, String code, String message) {
    return new VocabularyException(status, code, message, List.of(), Map.of());
  }

  public static VocabularyException invalid(String field, String message) {
    return new VocabularyException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "VALIDATION_FAILED",
        message,
        List.of(new FieldError(field, message)),
        Map.of());
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public List<FieldError> errors() {
    return errors;
  }

  public Map<String, Object> extra() {
    return extra;
  }
}
