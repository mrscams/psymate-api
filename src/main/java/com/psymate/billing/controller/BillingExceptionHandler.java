package com.psymate.billing.controller;

import com.psymate.billing.model.BillingException;
import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.FieldProblem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Erreurs RFC 9457 (application/problem+json) des contrôleurs de la facturation, sur le modèle de
 * l'agenda : errors[] = [{field, code, message}] pour les 422, statut du domaine pour les erreurs
 * métier ({@link BillingException}). Limité par package aux contrôleurs de {@code
 * com.psymate.billing.controller}. PATIENT_NOT_FOUND, levé par la garde de propriété des patients,
 * reste rendu par le handler commun de shared, comme le 422 UNKNOWN_FIELD de {@link
 * BillingBodyAdvice}.
 *
 * <p>Codes racine (champ {@code code}) : VALIDATION_FAILED (422, errors[] par champ),
 * MALFORMED_REQUEST (400 : corps illisible sans champ identifiable, ou {id} de chemin qui n'est pas
 * un UUID), et les codes de la facturation (L5).
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(basePackageClasses = BillingExceptionHandler.class)
class BillingExceptionHandler {

  private final BillingMessages messages;

  BillingExceptionHandler(BillingMessages messages) {
    this.messages = messages;
  }

  /** 422 VALIDATION_FAILED : un problème par champ (le premier), messages localisés. */
  private ProblemDetail validation(List<FieldProblem> problems, Locale locale) {
    Map<String, FieldProblem> byField = new LinkedHashMap<>();
    problems.forEach(p -> byField.putIfAbsent(p.field(), p));
    List<FieldProblem> errors = new ArrayList<>();
    byField
        .values()
        .forEach(
            p -> errors.add(new FieldProblem(p.field(), p.code(), messages.message(p, locale))));
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    pd.setTitle(messages.get("billing.error.validation.title", locale));
    pd.setDetail(messages.get("billing.error.validation.detail", locale));
    pd.setProperty("code", "VALIDATION_FAILED");
    pd.setProperty("errors", errors);
    return pd;
  }

  private ProblemDetail malformed(Locale locale) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    pd.setTitle(messages.get("billing.error.malformed.title", locale));
    pd.setDetail(messages.get("billing.error.malformed.detail", locale));
    pd.setProperty("code", "MALFORMED_REQUEST");
    return pd;
  }

  /** Code stable à partir du nom de la contrainte Jakarta (NotBlank, Min, Positive...). */
  static String codeOf(String constraint) {
    return switch (constraint) {
      case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
      case "Min", "Max", "Size", "Positive", "PositiveOrZero", "Range" -> "OUT_OF_RANGE";
      case "Pattern", "AssertTrue" -> "INVALID_VALUE";
      default -> "INVALID";
    };
  }

  /**
   * Erreur métier : statut et code de l'exception, message localisé. Une règle de champ hors
   * Jakarta (code racine VALIDATION_FAILED) prend la forme des erreurs Jakarta.
   */
  @ExceptionHandler(BillingException.class)
  ProblemDetail domain(
      BillingException ex, HttpServletRequest request, HttpServletResponse response) {
    Locale locale = messages.localeOf(request);
    if (!ex.fieldProblems().isEmpty()) {
      return validation(ex.fieldProblems(), locale);
    }
    String detail = messages.domain(ex.code(), ex.detail(), locale);
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.status(), detail);
    pd.setTitle(ex.title());
    pd.setProperty("code", ex.code());
    if (ex.retryAfterSeconds() != null) {
      // Comme le 429 RATE_LIMITED de shared : en-tête Retry-After et champ retryAfterSeconds.
      pd.setProperty("retryAfterSeconds", ex.retryAfterSeconds());
      response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()));
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
    return pd;
  }

  /** Contraintes Jakarta des corps de requête (@Valid). */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail bean(MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<FieldProblem> problems = new ArrayList<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      problems.add(new FieldProblem(fe.getField(), codeOf(String.valueOf(fe.getCode()))));
    }
    return validation(problems, messages.localeOf(request));
  }

  /**
   * Paramètre illisible : une variable de chemin qui n'est pas un UUID est un 400 (l'URL elle-même
   * est mal formée), un paramètre de requête un 422 sur ce paramètre.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail parameter(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    if (ex.getParameter().hasParameterAnnotation(PathVariable.class)) {
      return malformed(locale);
    }
    return validation(List.of(new FieldProblem(ex.getName(), "INVALID_FORMAT")), locale);
  }

  /** Corps illisible : 422 par champ si Jackson sait lequel, sinon 400. */
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
}
