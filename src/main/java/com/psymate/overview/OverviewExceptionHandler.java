package com.psymate.overview;

import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.FieldProblem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs RFC 9457 (application/problem+json) de la vue d'ensemble : 400 OVERVIEW_BAD_REQUEST avec
 * errors[] = [{field, code, message}]. Les 403 / 404 de la garde de propriété du patient sont des
 * {@code PatientAccessException}, rendues par le handler commun de shared.
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(assignableTypes = OverviewController.class)
class OverviewExceptionHandler {

  private final OverviewMessages messages;

  OverviewExceptionHandler(OverviewMessages messages) {
    this.messages = messages;
  }

  @ExceptionHandler(OverviewRequestException.class)
  ProblemDetail invalid(OverviewRequestException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    pd.setTitle(messages.get("overview.error.title", locale));
    pd.setDetail(messages.get("overview.error.detail", locale));
    pd.setProperty("code", "OVERVIEW_BAD_REQUEST");
    pd.setProperty(
        "errors",
        List.of(new FieldProblem(ex.field(), ex.code(), messages.forCode(ex.code(), locale))));
    return pd;
  }
}
