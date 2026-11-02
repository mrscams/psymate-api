package com.psymate.search.controller;

import com.psymate.search.model.SearchRequestException;
import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.FieldProblem;
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

/**
 * Erreurs RFC 9457 (application/problem+json) de la recherche : 400 SEARCH_BAD_REQUEST avec
 * errors[] = [{field, code, message}]. Les 403 / 404 de la garde de propriété du patient sont des
 * {@code PatientAccessException}, rendues par le handler commun de shared.
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(assignableTypes = SearchController.class)
class SearchExceptionHandler {

  private final SearchMessages messages;

  SearchExceptionHandler(SearchMessages messages) {
    this.messages = messages;
  }

  private ProblemDetail badRequest(List<FieldProblem> problems, Locale locale) {
    List<FieldProblem> errors = new ArrayList<>();
    problems.forEach(
        p ->
            errors.add(
                new FieldProblem(
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
}
