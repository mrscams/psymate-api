package com.psymate.clinical.controller;

import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.shared.problem.FieldProblem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Messages localisés des erreurs de champ (messages_fr.properties, messages_en.properties). Le
 * français est la langue de repli. Clé la plus précise d'abord : entry.error.CODE.champ, puis
 * entry.error.CODE.
 */
@Component
class ClinicalMessages {

  private final MessageSource source;

  ClinicalMessages() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    this.source = s;
  }

  private static Locale normalize(Locale locale) {
    return ServedLanguage.messageLocaleOf(locale);
  }

  /**
   * Français sans en-tête Accept-Language (langue par défaut du produit), sinon la langue demandée
   * ({@link ServedLanguage#ofRequestLocale}).
   */
  Locale localeOf(HttpServletRequest request) {
    return ServedLanguage.ofRequestLocale(request).locale();
  }

  String get(String key, Locale locale) {
    return source.getMessage(key, null, normalize(locale));
  }

  /** Message d'une erreur de champ ; à défaut de clé, le code lui-même. */
  String message(FieldProblem problem, Locale locale) {
    Locale l = normalize(locale);
    String generic = "entry.error." + problem.code();
    String field = problem.field().replaceAll("\\[\\d+]", "");
    return source.getMessage(
        generic + "." + field, null, source.getMessage(generic, null, problem.code(), l), l);
  }
}
