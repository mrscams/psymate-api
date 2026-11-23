package com.psymate.clinical.export.service;

import com.psymate.shared.i18n.ServedLanguage;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Textes d'un export (clés {@code clinical.export.*} de messages_fr.properties et
 * messages_en.properties, nom du produit {@code app.name}). Le français est la langue par défaut ;
 * l'anglais n'est servi que s'il est demandé.
 */
public final class ExportTexts {

  private static final MessageSource SOURCE = source();

  private final Locale locale;

  private ExportTexts(Locale locale) {
    this.locale = locale;
  }

  private static MessageSource source() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    return s;
  }

  /**
   * Textes dans la langue demandée : anglais si {@code en}, français sinon. L'arabe est résolu en
   * français, explicitement et toujours (G Q102) : les clés {@code clinical.export.*} ne sont pas
   * traduites et le PDF n'a pas de police arabe.
   */
  public static ExportTexts of(Locale requested) {
    return new ExportTexts(ServedLanguage.of(requested).arabicAsFrench().locale());
  }

  public Locale locale() {
    return locale;
  }

  /** Texte de la clé {@code clinical.export.<key>} ; la clé elle-même à défaut. */
  public String get(String key, Object... args) {
    return raw("clinical.export." + key, args);
  }

  /** Nom du produit (constante i18n {@code app.name}). */
  public String appName() {
    return raw("app.name");
  }

  private String raw(String code, Object... args) {
    return SOURCE.getMessage(code, args == null || args.length == 0 ? null : args, code, locale);
  }
}
