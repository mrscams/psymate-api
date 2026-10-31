package com.psymate.clinical.web;

import com.psymate.clinical.domain.ClinicalError;
import com.psymate.clinical.domain.ClinicalException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Erreurs RFC 9457 (application/problem+json) des contrôleurs clinical : errors[] = [{field, code,
 * message}] pour les 422, statut HTTP du domaine pour les erreurs métier, 403 pour la propriété.
 */
@RestControllerAdvice(
    assignableTypes = {EntryController.class, SessionController.class, TimelineController.class})
class ClinicalExceptionHandler {

  /** Entrée de errors[] telle que le client la reçoit. */
  record ApiFieldError(String field, String code, String message) {}

  private final ClinicalMessages messages;

  ClinicalExceptionHandler(ClinicalMessages messages) {
    this.messages = messages;
  }

  private ProblemDetail validation(List<FieldProblem> problems, Locale locale) {
    Map<String, FieldProblem> byField = new LinkedHashMap<>();
    problems.forEach(p -> byField.putIfAbsent(p.field(), p));
    List<ApiFieldError> errors = new ArrayList<>();
    byField
        .values()
        .forEach(
            p -> errors.add(new ApiFieldError(p.field(), p.code(), messages.message(p, locale))));
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    pd.setTitle(messages.get("error.validation.title", locale));
    pd.setDetail(messages.get("error.validation.detail", locale));
    pd.setProperty("code", "VALIDATION_FAILED");
    pd.setProperty("errors", errors);
    return pd;
  }

  @ExceptionHandler(EntryRequestException.class)
  ProblemDetail request(EntryRequestException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    return validation(ex.problems(), locale);
  }

  /** Filet de sécurité : une violation Jakarta qui aurait échappé à la pré-validation. */
  @ExceptionHandler(ConstraintViolationException.class)
  ProblemDetail constraints(ConstraintViolationException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    List<FieldProblem> problems = new ArrayList<>();
    for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
      problems.add(FieldProblem.of(v));
    }
    return validation(problems, locale);
  }

  /** Contraintes posées sur les requêtes de séance (@Valid). */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail bean(MethodArgumentNotValidException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    List<FieldProblem> problems = new ArrayList<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      problems.add(
          new FieldProblem(fe.getField(), FieldProblem.codeOf(String.valueOf(fe.getCode()))));
    }
    return validation(problems, locale);
  }

  /**
   * Paramètre de requête illisible (date non ISO, entier, booléen) : 422 sur ce paramètre. Une
   * variable de chemin illisible (un {id} qui n'est pas un UUID) reste un 400 : l'URL elle-même est
   * mal formée, pour tous les contrôleurs clinical.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail parameter(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    MethodParameter parameter = ex.getParameter();
    if (parameter.hasParameterAnnotation(PathVariable.class)) {
      return malformed(locale);
    }
    return validation(List.of(new FieldProblem(ex.getName(), "INVALID_FORMAT")), locale);
  }

  private ProblemDetail malformed(Locale locale) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    pd.setTitle(messages.get("error.malformed.title", locale));
    pd.setDetail(messages.get("error.malformed.detail", locale));
    pd.setProperty("code", "MALFORMED_REQUEST");
    return pd;
  }

  /** Corps illisible : 422 par champ si Jackson sait lequel (date, UUID, nombre), sinon 400. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    for (Throwable t = ex.getCause(); t != null; t = t.getCause()) {
      if (t instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
        return validation(List.of(new FieldProblem(path(mismatch), "INVALID_FORMAT")), locale);
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return malformed(locale);
  }

  private static String path(JacksonException ex) {
    StringBuilder sb = new StringBuilder();
    for (JacksonException.Reference ref : ex.getPath()) {
      if (ref.getPropertyName() != null) {
        sb.append(sb.isEmpty() ? "" : ".").append(ref.getPropertyName());
      } else if (ref.getIndex() >= 0) {
        sb.append('[').append(ref.getIndex()).append(']');
      }
    }
    return sb.toString();
  }

  /** Erreurs métier du domaine : le statut HTTP vient de ClinicalError. */
  @ExceptionHandler(ClinicalException.class)
  ProblemDetail domain(ClinicalException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.error().status());
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    pd.setTitle(status.getReasonPhrase());
    pd.setProperty("code", ex.error().name());
    if (status == HttpStatus.UNPROCESSABLE_CONTENT) {
      pd.setProperty(
          "errors",
          List.of(new ApiFieldError(fieldOf(ex.error()), ex.error().name(), ex.getMessage())));
    }
    return pd;
  }

  private static String fieldOf(ClinicalError error) {
    return switch (error) {
      case INVALID_TIME -> "occurred";
      case UNKNOWN_EMOTION -> "feeling.emotionCode";
      case SESSION_REQUIRED, SESSION_INVALID, SESSION_NOT_ALLOWED -> "sessionId";
      default -> "";
    };
  }

  /** 403 (patient d'un autre praticien) et 404 levés par la garde de propriété (patients). */
  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail ownership(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getReason());
    pd.setTitle(status.getReasonPhrase());
    pd.setProperty(
        "code", status == HttpStatus.FORBIDDEN ? "PATIENT_FORBIDDEN" : "PATIENT_NOT_FOUND");
    return pd;
  }
}
