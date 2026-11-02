package com.psymate.identity.admin.service;

import java.util.Locale;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Messages localisés de l'administration (messages_fr/en.properties, repli français). Le format
 * MessageFormat est toujours appliqué : les apostrophes s'écrivent doublées dans les clés admin.*.
 */
@Component
public class AdminMessages {

  private final ResourceBundleMessageSource source;

  AdminMessages() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    s.setAlwaysUseMessageFormat(true);
    this.source = s;
  }

  static Locale normalize(Locale locale) {
    return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
  }

  public String get(String key, Locale locale, Object... args) {
    return source.getMessage(key, args, normalize(locale));
  }
}
