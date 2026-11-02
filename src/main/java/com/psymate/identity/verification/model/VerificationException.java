package com.psymate.identity.verification.model;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Erreur métier du dossier professionnel, rendue en RFC 9457 par VerificationExceptionHandler. */
public class VerificationException extends RuntimeException {

  /** Erreur de champ : champ, code, clé de message. */
  public record FieldError(String field, String code, String messageKey) {}

  private final HttpStatus status;
  private final String code;
  private final String messageKey;
  private final List<FieldError> errors;
  private final Map<String, Object> extra;

  VerificationException(
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

  public static VerificationException of(HttpStatus status, String code, String messageKey) {
    return new VerificationException(status, code, messageKey, List.of(), Map.of());
  }

  public static VerificationException fields(List<FieldError> errors) {
    return new VerificationException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "VALIDATION_FAILED",
        "verification.error.invalid",
        errors,
        Map.of());
  }

  public static VerificationException incomplete(List<String> missing) {
    return new VerificationException(
        HttpStatus.CONFLICT,
        "VERIFICATION_INCOMPLETE",
        "verification.error.incomplete",
        List.of(),
        Map.of("missing", missing));
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
