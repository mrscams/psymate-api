package com.psymate.overview;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Messages localisés des erreurs de la vue d'ensemble (messages_fr.properties,
 * messages_en.properties, clés overview.error.*). Le français est la langue de repli.
 */
@Component
class OverviewMessages {

  private final MessageSource source;

  OverviewMessages() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    this.source = s;
  }

  private static Locale normalize(Locale locale) {
    return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
  }

  /** Français sans en-tête Accept-Language, sinon la langue demandée. */
  Locale localeOf(HttpServletRequest request) {
    String header = request.getHeader("Accept-Language");
    return header == null || header.isBlank() ? Locale.FRENCH : normalize(request.getLocale());
  }

  String get(String key, Locale locale) {
    return source.getMessage(key, null, normalize(locale));
  }

  /** Message d'un code d'erreur de champ (overview.error.CODE) ; à défaut, le code lui-même. */
  String forCode(String code, Locale locale) {
    return source.getMessage("overview.error." + code, null, code, normalize(locale));
  }
}
