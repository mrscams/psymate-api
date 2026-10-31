package com.psymate.identity.verification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erreurs RFC 9457 (application/problem+json) du dossier professionnel. */
@RestControllerAdvice(assignableTypes = VerificationController.class)
class VerificationExceptionHandler {

  private final VerificationMessages messages;

  VerificationExceptionHandler(VerificationMessages messages) {
    this.messages = messages;
  }

  @ExceptionHandler(VerificationException.class)
  ResponseEntity<ProblemDetail> handle(VerificationException ex, Locale locale) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(ex.status(), messages.get(ex.messageKey(), locale));
    pd.setTitle(ex.status().getReasonPhrase());
    pd.setProperty("code", ex.code());
    if (!ex.errors().isEmpty()) {
      List<Map<String, String>> errors = new ArrayList<>();
      for (VerificationException.FieldError e : ex.errors()) {
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
