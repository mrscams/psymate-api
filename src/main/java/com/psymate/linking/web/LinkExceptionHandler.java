package com.psymate.linking.web;

import com.psymate.linking.domain.GraphRequestException;
import com.psymate.linking.domain.LinkError;
import com.psymate.linking.domain.LinkException;
import com.psymate.shared.problem.AdviceOrder;
import com.psymate.vocabulary.api.LinkTypeView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Erreurs RFC 9457 (application/problem+json) du contrôleur de liens : errors[] = [{field, code,
 * message}] pour les 422, statut du domaine pour les erreurs métier, 403 / 404 de la garde de
 * propriété des patients. Un 409 LINK_EXISTS porte {@code existingLinkId}, un 422
 * LINK_TYPE_NOT_ALLOWED porte {@code allowedLinkTypes} (types possibles pour cette paire). Sert
 * aussi le contrôleur de graphe (M2-03).
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(assignableTypes = {LinkController.class, GraphController.class})
class LinkExceptionHandler {

  /** Entrée de errors[] telle que le client la reçoit. */
  record ApiFieldError(String field, String code, String message) {}

  /** Type de lien proposé au client quand le sien est refusé. */
  record AllowedLinkType(String code, String label) {}

  private final LinkMessages messages;

  LinkExceptionHandler(LinkMessages messages) {
    this.messages = messages;
  }

  @ExceptionHandler(LinkException.class)
  ProblemDetail domain(LinkException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    LinkError error = ex.error();
    HttpStatus status = HttpStatus.valueOf(error.status());
    List<AllowedLinkType> allowed =
        ex.allowedTypes().stream()
            .map(t -> new AllowedLinkType(t.code(), labelOf(t, locale)))
            .toList();
    String detail = detail(ex, allowed, locale);
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setTitle(status.getReasonPhrase());
    pd.setProperty("code", error.name());
    if (error == LinkError.ENTRY_ARCHIVED) {
      pd.setProperty("errors", List.of(new ApiFieldError(ex.field(), error.name(), detail)));
    }
    if (status == HttpStatus.UNPROCESSABLE_CONTENT) {
      String field = ex.field() != null ? ex.field() : fieldOf(error);
      pd.setProperty("errors", List.of(new ApiFieldError(field, error.name(), detail)));
    }
    if (error == LinkError.LINK_EXISTS) {
      pd.setProperty("existingLinkId", ex.existingLinkId());
    }
    if (error == LinkError.LINK_TYPE_NOT_ALLOWED) {
      pd.setProperty("allowedLinkTypes", allowed);
    }
    return pd;
  }

  private String detail(LinkException ex, List<AllowedLinkType> allowed, Locale locale) {
    String key = "link.error." + ex.error().name();
    if (ex.error() == LinkError.LINK_TYPE_NOT_ALLOWED) {
      if (allowed.isEmpty()) {
        return messages.get(key + ".none", locale, ex.getMessage());
      }
      String labels = String.join(", ", allowed.stream().map(AllowedLinkType::label).toList());
      return messages.get(key, locale, ex.getMessage(), labels);
    }
    String generic = messages.get(key, locale, ex.getMessage());
    return ex.field() == null ? generic : messages.get(key + "." + ex.field(), locale, generic);
  }

  private static String labelOf(LinkTypeView type, Locale locale) {
    return "en".equals(locale.getLanguage()) ? type.labelEn() : type.labelFr();
  }

  private static String fieldOf(LinkError error) {
    return switch (error) {
      case SELF_LINK, DIFFERENT_PATIENT -> "targetId";
      case UNKNOWN_LINK_TYPE, LINK_TYPE_NOT_ALLOWED -> "linkType";
      default -> "";
    };
  }

  private ProblemDetail validation(List<FieldProblem> problems, Locale locale) {
    List<ApiFieldError> errors = new ArrayList<>();
    problems.stream()
        .filter(p -> errors.stream().noneMatch(e -> e.field().equals(p.field())))
        .forEach(
            p ->
                errors.add(
                    new ApiFieldError(
                        p.field(),
                        p.code(),
                        messages.get(
                            "link.error." + p.code() + "." + p.field(),
                            locale,
                            messages.get("link.error." + p.code(), locale, p.code())))));
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    pd.setTitle(messages.get("link.error.validation.title", locale));
    pd.setDetail(messages.get("link.error.validation.detail", locale));
    pd.setProperty("code", "VALIDATION_FAILED");
    pd.setProperty("errors", errors);
    return pd;
  }

  /** Paramètres de graphe refusés (profondeur, période, types, groupe à développer). */
  @ExceptionHandler(GraphRequestException.class)
  ProblemDetail graphRequest(GraphRequestException ex, HttpServletRequest request) {
    return validation(
        ex.problems().stream().map(p -> new FieldProblem(p.field(), p.code())).toList(),
        messages.localeOf(request));
  }

  /** Contraintes Jakarta des corps de requête. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail bean(MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<FieldProblem> problems = new ArrayList<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      problems.add(
          new FieldProblem(fe.getField(), FieldProblem.codeOf(String.valueOf(fe.getCode()))));
    }
    return validation(problems, messages.localeOf(request));
  }

  /** Corps illisible : 422 par champ si Jackson sait lequel (UUID, type), sinon 400. */
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
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    pd.setTitle(messages.get("link.error.malformed.title", locale));
    pd.setDetail(messages.get("link.error.malformed.detail", locale));
    pd.setProperty("code", "MALFORMED_REQUEST");
    return pd;
  }

  private static String path(JacksonException ex) {
    StringBuilder sb = new StringBuilder();
    for (JacksonException.Reference ref : ex.getPath()) {
      if (ref.getPropertyName() != null) {
        sb.append(sb.isEmpty() ? "" : ".").append(ref.getPropertyName());
      }
    }
    return sb.toString();
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
