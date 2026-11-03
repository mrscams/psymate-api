package com.psymate.patients.controller;

import com.psymate.patients.model.PatientException;
import com.psymate.patients.model.PatientFieldCode;
import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.ErrorCode;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemMessages;
import com.psymate.shared.problem.Problems;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/** Erreurs RFC 9457 limitées au contrôleur du module patients. */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(assignableTypes = PatientController.class)
class PatientExceptionHandler {

  @ExceptionHandler(PatientException.class)
  ProblemDetail handle(PatientException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    pd.setTitle(ex.status().getReasonPhrase());
    pd.setProperty("code", ex.code());
    if (!ex.errors().isEmpty()) {
      pd.setProperty("errors", ex.errors());
    }
    return pd;
  }

  /**
   * Corps illisible : 422 INVALID_FORMAT sur le champ quand Jackson sait lequel (date de naissance
   * illisible, liste attendue...), comme clinical ; sinon 400 MALFORMED_JSON, comme le handler
   * commun.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
    Locale locale = ProblemMessages.localeOf(request);
    for (Throwable t = ex.getCause(); t != null; t = t.getCause()) {
      if (t instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
        String message = ProblemMessages.invalidValue(locale);
        return Problems.of(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ErrorCode.VALIDATION_FAILED,
            HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
            message,
            List.of(new FieldProblem(path(mismatch), PatientFieldCode.INVALID_FORMAT, message)));
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return Problems.of(
        HttpStatus.BAD_REQUEST,
        ErrorCode.MALFORMED_JSON,
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        ProblemMessages.detail(ErrorCode.MALFORMED_JSON, locale));
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
