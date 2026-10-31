package com.psymate.identity.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erreurs RFC 9457 (application/problem+json) de l'administration des dossiers. */
@RestControllerAdvice(assignableTypes = AdminVerificationController.class)
class AdminExceptionHandler {

  private final AdminMessages messages;

  AdminExceptionHandler(AdminMessages messages) {
    this.messages = messages;
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
