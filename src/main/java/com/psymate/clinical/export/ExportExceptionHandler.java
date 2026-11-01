package com.psymate.clinical.export;

import com.psymate.shared.problem.AdviceOrder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 403 et 404 levés par la garde de propriété du patient (patients), avec les mêmes codes que les
 * autres contrôleurs de clinical (PATIENT_FORBIDDEN, PATIENT_NOT_FOUND). Les erreurs propres à
 * l'export (422, 404 EXPORT_NOT_FOUND, 409, 410, 500) sont des ProblemException rendues par le
 * handler global ; les erreurs d'authentification par identifiants, par celui d'identity
 * (AuthErrorScope).
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(assignableTypes = PatientExportController.class)
class ExportExceptionHandler {

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
