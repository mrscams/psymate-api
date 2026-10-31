package com.psymate.identity.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs RFC 9457 (application/problem+json) de l'administration : dossiers, comptes, journal,
 * recours.
 */
@RestControllerAdvice(
    assignableTypes = {
      AdminVerificationController.class,
      AdminPractitionerController.class,
      AdminJournalController.class,
      AppealController.class
    })
class AdminExceptionHandler {

  private final AdminMessages messages;

  AdminExceptionHandler(AdminMessages messages) {
    this.messages = messages;
  }

  /** Corps absent ou JSON illisible : 400 en problem+json, comme le module clinical. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> malformed(Locale locale) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, messages.get("error.malformed.detail", locale));
    pd.setTitle(messages.get("error.malformed.title", locale));
    pd.setProperty("code", "MALFORMED_REQUEST");
    return ResponseEntity.badRequest().body(pd);
  }

  @ExceptionHandler(AdminException.class)
  ResponseEntity<ProblemDetail> handle(AdminException ex, Locale locale) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(ex.status(), messages.get(ex.messageKey(), locale));
    pd.setTitle(ex.status().getReasonPhrase());
    pd.setProperty("code", ex.code());
    if (!ex.errors().isEmpty()) {
      List<Map<String, String>> errors = new ArrayList<>();
      for (AdminException.FieldError e : ex.errors()) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("field", e.field());
        m.put("code", e.code());
        m.put("message", messages.get(e.messageKey(), locale));
        errors.add(m);
      }
      pd.setProperty("errors", errors);
    }
    ex.extra().forEach(pd::setProperty);
    return ResponseEntity.status(ex.status()).body(pd);
  }
}
