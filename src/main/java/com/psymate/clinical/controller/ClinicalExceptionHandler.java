package com.psymate.clinical.controller;

import com.psymate.clinical.api.EntryLinkException;
import com.psymate.clinical.model.ClinicalError;
import com.psymate.clinical.model.ClinicalException;
import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.FieldProblem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
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
 * Erreurs RFC 9457 (application/problem+json) des contrôleurs clinical : errors[] = [{field, code,
 * message}] pour les 422, statut HTTP du domaine pour les erreurs métier.
 *
 * <p>Codes racine produits (champ {@code code}) : VALIDATION_FAILED (422, errors[] par champ),
 * MALFORMED_REQUEST (400 : corps JSON illisible sans champ identifiable, ou identifiant de chemin
 * qui n'est pas un UUID), et le nom de chaque {@link ClinicalError} (statut du domaine ; pour un
 * 422, errors[0] porte le même code). PATIENT_FORBIDDEN (403) et PATIENT_NOT_FOUND (404), levés par
 * la garde de propriété, sont des {@code PatientAccessException} rendues par le handler commun de
 * shared.
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(
    assignableTypes = {EntryController.class, SessionController.class, TimelineController.class})
class ClinicalExceptionHandler {

  private final ClinicalMessages messages;

  ClinicalExceptionHandler(ClinicalMessages messages) {
    this.messages = messages;
  }

  private ProblemDetail validation(List<FieldProblem> problems, Locale locale) {
    Map<String, FieldProblem> byField = new LinkedHashMap<>();
    problems.forEach(p -> byField.putIfAbsent(p.field(), p));
    List<FieldProblem> errors = new ArrayList<>();
    byField
        .values()
        .forEach(
            p -> errors.add(new FieldProblem(p.field(), p.code(), messages.message(p, locale))));
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

  /**
   * Un lien demandé à la création est refusé (M2-02) : 422 comme une erreur de validation, sur le
   * champ links[i].linkType ou links[i].targetId ; l'entrée n'est pas créée. Pour
   * LINK_TYPE_NOT_ALLOWED, allowedLinkTypes liste les types possibles pour ce couple.
   */
  @ExceptionHandler(EntryLinkException.class)
  ProblemDetail link(EntryLinkException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    ProblemDetail pd =
        validation(
            List.of(new FieldProblem("links[" + ex.index() + "]." + ex.field(), ex.code())),
            locale);
    if (!ex.allowedLinkTypes().isEmpty()) {
      pd.setProperty("allowedLinkTypes", ex.allowedLinkTypes());
    }
    return pd;
  }

  /** Filet de sécurité : une violation Jakarta qui aurait échappé à la pré-validation. */
  @ExceptionHandler(ConstraintViolationException.class)
  ProblemDetail constraints(ConstraintViolationException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    List<FieldProblem> problems = new ArrayList<>();
    for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
      problems.add(FieldProblems.of(v));
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
          new FieldProblem(fe.getField(), FieldProblems.codeOf(String.valueOf(fe.getCode()))));
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
          List.of(new FieldProblem(fieldOf(ex.error()), ex.error().name(), ex.getMessage())));
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
}
