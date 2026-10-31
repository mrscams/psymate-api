package com.psymate.identity.preferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erreurs RFC 9457 des préférences : 422 avec errors[] par clé fautive. */
@RestControllerAdvice(assignableTypes = PreferencesController.class)
class PreferencesExceptionHandler {

  private final MessageSource source;

  PreferencesExceptionHandler() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    this.source = s;
  }

  @ExceptionHandler(PreferencesService.InvalidPreferencesException.class)
  ResponseEntity<ProblemDetail> invalid(
      PreferencesService.InvalidPreferencesException ex, Locale locale) {
    Locale l = locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_CONTENT,
            source.getMessage("preferences.error.invalid", null, l));
    pd.setTitle(HttpStatus.UNPROCESSABLE_CONTENT.getReasonPhrase());
    pd.setProperty("code", "VALIDATION_FAILED");
    List<Map<String, String>> errors = new ArrayList<>();
    for (Preferences.Violation v : ex.violations()) {
      Map<String, String> m = new LinkedHashMap<>();
      m.put("field", v.key());
      m.put("code", v.code());
      m.put("message", source.getMessage("preferences.error." + v.key(), null, l));
      errors.add(m);
    }
    pd.setProperty("errors", errors);
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(pd);
  }
}
