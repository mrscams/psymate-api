package com.psymate.patients.controller;

import com.psymate.patients.model.PatientException;
import com.psymate.shared.problem.AdviceOrder;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
