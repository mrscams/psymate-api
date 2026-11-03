package com.psymate.patients.model;

import com.psymate.shared.problem.ErrorCode;
import com.psymate.shared.problem.FieldProblem;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Erreur métier du module, rendue en RFC 9457 avec un champ {@code code} ; pour un 422, {@code
 * errors[]} liste des {@link FieldProblem} {field, code, message} comme les autres modules.
 */
public class PatientException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final List<FieldProblem> errors;

  public PatientException(
      HttpStatus status, String code, String message, List<FieldProblem> errors) {
    super(message);
    this.status = status;
    this.code = code;
    this.errors = errors;
  }

  public static PatientException of(HttpStatus status, String code, String message) {
    return new PatientException(status, code, message, List.of());
  }

  /** 422 VALIDATION_FAILED sur un seul champ, avec le code de champ et son message. */
  public static PatientException invalid(String field, String fieldCode, String message) {
    return new PatientException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        ErrorCode.VALIDATION_FAILED,
        message,
        List.of(new FieldProblem(field, fieldCode, message)));
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public List<FieldProblem> errors() {
    return errors;
  }
}
