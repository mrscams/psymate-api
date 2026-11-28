package com.psymate.billing.service;

import com.psymate.shared.i18n.ServedLanguage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Textes des documents de facturation, toujours lus dans {@code messages_fr.properties}, quelle que
 * soit la langue servie (G Q92 : mentions de TVA ; les factures et leurs modèles d'e-mail restent
 * en français, comme la feuille de facture du web, G Q70 du web). Clés {@code
 * billing.vat.mention.*}, {@code billing.title.*}, {@code billing.template.*}, {@code
 * billing.pdf.*} (feuille PDF, M15-09) et {@code billing.line.*} (désignation d'une ligne de
 * séance, M15-08) ; messages_en et messages_ar en portent une copie identique pour la parité des
 * fichiers, jamais lue. Les messages d'erreur, eux, suivent la langue servie ({@code
 * BillingMessages}).
 */
@Component
public class BillingTexts {

  private static final Locale FRENCH = ServedLanguage.FR.locale();

  /** Date d'une désignation de ligne, à la française, motif fixe sans dépendance à la langue. */
  private static final DateTimeFormatter LINE_DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);

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

  /**
   * Désignation d'une ligne de séance (M15-08), sans aucun texte clinique (G Q83) : « {libellé de
   * la prestation} du 05/11/2026 » si une prestation est retenue, sinon « Séance du 05/11/2026 ».
   * Accolades {@code {label}} et {@code {date}} remplacées ici (message lu sans MessageFormat).
   */
  public String sessionLineLabel(String serviceLabel, LocalDate day) {
    String date = LINE_DATE.format(day);
    if (serviceLabel == null || serviceLabel.isBlank()) {
      return source.getMessage("billing.line.session", null, FRENCH).replace("{date}", date);
    }
    return source
        .getMessage("billing.line.service", null, FRENCH)
        .replace("{date}", date)
        .replace("{label}", serviceLabel.strip());
  }

  /**
   * Texte fixe de la feuille PDF (M15-09) : clé {@code billing.pdf.<key>}, toujours en français (G
   * Q93), lu sans MessageFormat : les accolades nommées ({@code {number}}) sont remplacées par
   * l'appelant.
   */
  public String pdf(String key) {
    return source.getMessage("billing.pdf." + key, null, FRENCH);
  }

  /** Texte par défaut d'un modèle d'e-mail : {@code kind} invoice ou reminder, {@code part}. */
  public String template(String kind, String part) {
    return source.getMessage("billing.template." + kind + "." + part, null, FRENCH);
  }
}
