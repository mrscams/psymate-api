package com.psymate.identity.auth;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Messages localisés (messages_fr.properties, messages_en.properties). Source privée au module : le
 * français est la langue de repli, il n'y a pas de fichier messages.properties de base.
 */
@Component
class AuthMessages {

  private final MessageSource source;

  AuthMessages() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    this.source = s;
  }

  static Locale normalize(Locale locale) {
    return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
  }

  String get(String key, Locale locale, Object... args) {
    return source.getMessage(key, args, normalize(locale));
  }
}
