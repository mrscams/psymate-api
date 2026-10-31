package com.psymate.linking.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Messages localisés du module (messages_fr.properties, messages_en.properties, clés link.*). Le
 * français est la langue de repli, et la langue des requêtes sans Accept-Language.
 */
@Component
class LinkMessages {

  private final MessageSource source;

  LinkMessages() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    this.source = s;
  }

  private static Locale normalize(Locale locale) {
    return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
  }

  Locale localeOf(HttpServletRequest request) {
    String header = request.getHeader("Accept-Language");
    return header == null || header.isBlank() ? Locale.FRENCH : normalize(request.getLocale());
  }

  /** Message de la clé, ou {@code fallback} si elle n'existe pas. */
  String get(String key, Locale locale, String fallback, Object... args) {
    return source.getMessage(key, args.length == 0 ? null : args, fallback, normalize(locale));
  }

  String get(String key, Locale locale) {
    return get(key, locale, key);
  }
}
