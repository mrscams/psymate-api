package com.psymate.identity.verification;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/** Messages localisés du dossier professionnel (messages_fr/en.properties, repli français). */
@Component
class VerificationMessages {

  private final MessageSource source;

  VerificationMessages() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    this.source = s;
  }

  String get(String key, Locale locale, Object... args) {
    Locale l = locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
    return source.getMessage(key, args, l);
  }
}
