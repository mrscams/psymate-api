package com.psymate.billing.controller;

import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.shared.problem.FieldProblem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Messages localisés de la facturation (messages_fr, messages_en, messages_ar ; clés {@code
 * billing.error.*}). Pour une erreur de champ, clé la plus précise d'abord : {@code
 * billing.error.CODE.champ}, puis {@code billing.error.CODE}, puis le code. Ces messages sont ceux
 * des réponses d'erreur de l'API ; les documents (factures, avoirs, reçus) restent en français.
 */
@Component
class BillingMessages {

  private final MessageSource source;

  BillingMessages() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    this.source = s;
  }

  private static Locale normalize(Locale locale) {
    return ServedLanguage.messageLocaleOf(locale);
  }

  /** Langue servie de la requête ({@link ServedLanguage#ofRequestLocale}), français par défaut. */
  Locale localeOf(HttpServletRequest request) {
    return ServedLanguage.ofRequestLocale(request).locale();
  }

  String get(String key, Locale locale) {
    return source.getMessage(key, null, normalize(locale));
  }

  /** Message d'un code métier ({@code billing.error.CODE}) ; à défaut de clé, {@code fallback}. */
  String domain(String code, String fallback, Locale locale) {
    return source.getMessage("billing.error." + code, null, fallback, normalize(locale));
  }

  /** Message d'une erreur de champ ; à défaut de clé, le code lui-même. */
  String message(FieldProblem problem, Locale locale) {
    Locale l = normalize(locale);
    String generic = "billing.error." + problem.code();
    String field = problem.field().replaceAll("\\[\\d+]", "");
    return source.getMessage(
        generic + "." + field, null, source.getMessage(generic, null, problem.code(), l), l);
  }
}
