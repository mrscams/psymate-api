package com.psymate.search;

import com.psymate.search.SearchRequestException.FieldProblem;
import com.psymate.shared.problem.AdviceOrder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Erreurs RFC 9457 (application/problem+json) de la recherche : 400 SEARCH_BAD_REQUEST avec
 * errors[] = [{field, code, message}], et les 403 / 404 de la garde de propriété du patient.
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(assignableTypes = SearchController.class)
class SearchExceptionHandler {

  /** Entrée de errors[] telle que le client la reçoit. */
  record ApiFieldError(String field, String code, String message) {}

  private final SearchMessages messages;

  SearchExceptionHandler(SearchMessages messages) {
    this.messages = messages;
  }

  private ProblemDetail badRequest(List<FieldProblem> problems, Locale locale) {
    List<ApiFieldError> errors = new ArrayList<>();
    problems.forEach(
        p ->
            errors.add(
                new ApiFieldError(
                    p.field(), p.code(), messages.forCode(p.field(), p.code(), locale))));
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    pd.setTitle(messages.get("search.error.title", locale));
    pd.setDetail(messages.get("search.error.detail", locale));
    pd.setProperty("code", "SEARCH_BAD_REQUEST");
    pd.setProperty("errors", errors);
    return pd;
  }

  @ExceptionHandler(SearchRequestException.class)
  ProblemDetail invalid(SearchRequestException ex, HttpServletRequest request) {
    return badRequest(ex.problems(), messages.localeOf(request));
  }

  /** Paramètre non convertible (page=abc, sessionId mal formé, includeArchived=peut-être). */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail mismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    return badRequest(
        List.of(new FieldProblem(ex.getName(), "INVALID_FORMAT")), messages.localeOf(request));
  }

  /** 403 (patient d'un autre praticien) et 404 levés par la garde de propriété (patients). */
  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail ownership(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getReason());
    pd.setTitle(status.getReasonPhrase());
    if (status == HttpStatus.FORBIDDEN) {
      pd.setProperty("code", "PATIENT_FORBIDDEN");
    } else if (status == HttpStatus.NOT_FOUND) {
      pd.setProperty("code", "PATIENT_NOT_FOUND");
    }
    return pd;
  }
}
