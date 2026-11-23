package com.psymate.agenda.controller;

import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.shared.problem.FieldProblem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Messages localisés de l'agenda (messages_fr.properties, messages_en.properties ; clés {@code
 * agenda.error.*}). Le français est la langue de repli. Pour une erreur de champ, clé la plus
 * précise d'abord : {@code agenda.error.CODE.champ}, puis {@code agenda.error.CODE}, puis le code.
 */
@Component
class AgendaMessages {

  private final MessageSource source;

  AgendaMessages() {
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
   * Français sans en-tête Accept-Language (langue du produit), sinon la langue demandée ({@link
   * ServedLanguage#ofRequestLocale}).
   */
  Locale localeOf(HttpServletRequest request) {
    return ServedLanguage.ofRequestLocale(request).locale();
  }

  String get(String key, Locale locale) {
    return source.getMessage(key, null, normalize(locale));
  }

  /** Message d'un code métier ({@code agenda.error.CODE}) ; à défaut de clé, {@code fallback}. */
  String domain(String code, String fallback, Locale locale) {
    return source.getMessage("agenda.error." + code, null, fallback, normalize(locale));
  }

  /**
   * Message d'une erreur de champ dans un contexte ({@code scope}, ou null) : clé {@code
   * agenda.error.CODE.scope.champ} d'abord, puis les clés sans contexte.
   */
  String message(FieldProblem problem, Locale locale, String scope) {
    String fallback = message(problem, locale);
    if (scope == null) {
      return fallback;
    }
    String field = problem.field().replaceAll("\\[\\d+]", "");
    String key = "agenda.error." + problem.code() + "." + scope + "." + field;
    return source.getMessage(key, null, fallback, normalize(locale));
  }

  /** Message d'une erreur de champ ; à défaut de clé, le code lui-même. */
  String message(FieldProblem problem, Locale locale) {
    Locale l = normalize(locale);
    String generic = "agenda.error." + problem.code();
    String field = problem.field().replaceAll("\\[\\d+]", "");
    return source.getMessage(
        generic + "." + field, null, source.getMessage(generic, null, problem.code(), l), l);
  }
}
