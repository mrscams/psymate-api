package com.psymate.billing.service;

import com.psymate.shared.i18n.ServedLanguage;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Textes des documents de facturation, toujours lus dans {@code messages_fr.properties}, quelle que
 * soit la langue servie (G Q92 : mentions de TVA ; les factures et leurs modèles d'e-mail restent
 * en français, comme la feuille de facture du web, G Q70 du web). Clés {@code
 * billing.vat.mention.*}, {@code billing.title.*} et {@code billing.template.*} ; messages_en et
 * messages_ar en portent une copie identique pour la parité des fichiers, jamais lue. Les messages
 * d'erreur, eux, suivent la langue servie ({@code BillingMessages}).
 */
@Component
public class BillingTexts {

  private static final Locale FRENCH = ServedLanguage.FR.locale();

  private final MessageSource source;

  public BillingTexts() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    this.source = s;
  }

  /**
   * Mention légale du régime de TVA (texte de G Q92 mot pour mot) ; null pour le régime assujetti,
   * qui n'a pas de mention d'exonération.
   */
  public String vatMention(String regime) {
    return source.getMessage("billing.vat.mention." + regime, null, null, FRENCH);
  }

  /** Titre imprimé d'une profession (Psychologue…), null si la profession est inconnue. */
  public String title(String profession) {
    if (profession == null) {
      return null;
    }
    return source.getMessage("billing.title." + profession, null, null, FRENCH);
  }

  /** Texte par défaut d'un modèle d'e-mail : {@code kind} invoice ou reminder, {@code part}. */
  public String template(String kind, String part) {
    return source.getMessage("billing.template." + kind + "." + part, null, FRENCH);
  }
}
