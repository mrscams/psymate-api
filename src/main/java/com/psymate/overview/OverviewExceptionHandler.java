package com.psymate.overview;

import com.psymate.shared.problem.AdviceOrder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Erreurs RFC 9457 (application/problem+json) de la vue d'ensemble : 400 OVERVIEW_BAD_REQUEST avec
 * errors[] = [{field, code, message}], et les 403 / 404 de la garde de propriété du patient.
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(assignableTypes = OverviewController.class)
class OverviewExceptionHandler {

  /** Entrée de errors[] telle que le client la reçoit. */
  record ApiFieldError(String field, String code, String message) {}

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
        List.of(new ApiFieldError(ex.field(), ex.code(), messages.forCode(ex.code(), locale))));
    return pd;
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
