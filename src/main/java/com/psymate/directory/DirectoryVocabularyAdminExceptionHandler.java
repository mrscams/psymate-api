package com.psymate.directory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs RFC 9457 (application/problem+json) de l'administration du vocabulaire de l'annuaire,
 * avec errors[] par champ (champ, code, message). Messages en français par défaut, en anglais si la
 * langue demandée l'est (messages_fr/en.properties, clés vocabulary.error.*).
 */
@RestControllerAdvice(assignableTypes = DirectoryVocabularyAdminController.class)
class DirectoryVocabularyAdminExceptionHandler {

  private final ResourceBundleMessageSource messages;

  DirectoryVocabularyAdminExceptionHandler() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    s.setAlwaysUseMessageFormat(true);
    this.messages = s;
  }

  private String text(String key, Locale locale) {
    Locale l = locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
    return messages.getMessage(key, null, l);
  }

  @ExceptionHandler(VocabularyException.class)
  ResponseEntity<ProblemDetail> handle(VocabularyException ex, Locale locale) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.status(), text(ex.messageKey(), locale));
    pd.setTitle(ex.status().getReasonPhrase());
    pd.setProperty("code", ex.code());
    if (!ex.errors().isEmpty()) {
      List<Map<String, String>> errors = new ArrayList<>();
      for (VocabularyException.FieldError e : ex.errors()) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("field", e.field());
        m.put("code", e.code());
        m.put("message", text(e.messageKey(), locale));
        errors.add(m);
      }
      pd.setProperty("errors", errors);
    }
    ex.extra().forEach(pd::setProperty);
    return error(pd);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> handle(HttpMessageNotReadableException ex, Locale locale) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, text("vocabulary.error.body", locale));
    pd.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
    pd.setProperty("code", "INVALID_BODY");
    return error(pd);
  }

  private static ResponseEntity<ProblemDetail> error(ProblemDetail pd) {
    return ResponseEntity.status(pd.getStatus())
        .header("Cache-Control", "no-store")
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(pd);
  }
}
