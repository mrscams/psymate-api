package com.psymate.patients;

import com.psymate.shared.problem.AdviceOrder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

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

  /** 403 / 404 levés par la garde de propriété. */
  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail handle(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getReason());
    pd.setTitle(status.getReasonPhrase());
    pd.setProperty(
        "code", status == HttpStatus.FORBIDDEN ? "PATIENT_FORBIDDEN" : "PATIENT_NOT_FOUND");
    return pd;
  }
}
