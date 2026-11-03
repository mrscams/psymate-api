package com.psymate.directory.model;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Erreur métier du module, rendue en RFC 9457 avec un champ {@code code}. */
public class ProfileException extends RuntimeException {

  public record FieldError(String field, String message) {}

  /** Erreur de champ avec code machine (ex. INVALID_VALUE), rendue dans errors[]. */
  public record CodedFieldError(String field, String code, String message) {}

  private final HttpStatus status;
  private final String code;
  private final List<?> errors;
  private final Map<String, Object> extra;

  public ProfileException(
      HttpStatus status, String code, String message, List<?> errors, Map<String, Object> extra) {
    super(message);
    this.status = status;
    this.code = code;
    this.errors = errors;
    this.extra = extra;
  }

  public static ProfileException of(HttpStatus status, String code, String message) {
    return new ProfileException(status, code, message, List.of(), Map.of());
  }

  /** 422 VALIDATION_FAILED dont chaque erreur de champ porte un code. */
  public static ProfileException invalidCoded(List<CodedFieldError> errors) {
    return new ProfileException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "VALIDATION_FAILED",
        "Certains champs sont invalides.",
        errors,
        Map.of());
  }

  public static ProfileException invalid(List<FieldError> errors) {
    return new ProfileException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "VALIDATION_FAILED",
        "Certains champs sont invalides.",
        errors,
        Map.of());
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public List<?> errors() {
    return errors;
  }

  public Map<String, Object> extra() {
    return extra;
  }
}
