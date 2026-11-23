package com.psymate.linking.dto;

import com.psymate.shared.i18n.ServedLanguage;
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
public class LinkMessages {

  private final MessageSource source;

  LinkMessages() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    this.source = s;
  }

  private static Locale normalize(Locale locale) {
    return ServedLanguage.messageLocaleOf(locale);
  }

  public Locale localeOf(HttpServletRequest request) {
    return ServedLanguage.ofRequestLocale(request).locale();
  }

  /** Message de la clé, ou {@code fallback} si elle n'existe pas. */
  public String get(String key, Locale locale, String fallback, Object... args) {
    return source.getMessage(key, args.length == 0 ? null : args, fallback, normalize(locale));
  }

  public String get(String key, Locale locale) {
    return get(key, locale, key);
  }
}
