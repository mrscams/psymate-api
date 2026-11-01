package com.psymate.overview;

import com.psymate.overview.NarrativeFacts.Dominant;
import com.psymate.overview.NarrativeFacts.FlagFact;
import com.psymate.overview.NarrativeFacts.MedicationFact;
import com.psymate.overview.NarrativeFacts.Trend;
import com.psymate.overview.NarrativeFacts.UncommentedEvent;
import com.psymate.overview.OverviewDtos.NarrativeDto;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Assemble le résumé « En bref » (5 phrases au plus, 3 à 5 pour un dossier nourri) et le point
 * d'attention d'un patient, par règles, sans IA (B-ux, écran 2 : « comme un confrère le raconterait
 * », sans jugement ni vocabulaire diagnostique).
 *
 * <p>Décision d'architecture : la langue dépend de la requête, jamais du patient. La projection
 * calcule donc les <em>données structurées</em> du récit ({@link NarrativeFacts} : tendance et deux
 * moyennes, ressenti dominant avec ses libellés FR et EN, titres, dates, traitements, drapeaux,
 * rythme) et les stocke dans {@code patient_overview.payload} ; les <em>phrases</em> sont résolues
 * ici, à la lecture, dans la locale de la requête, à partir des fragments {@code
 * overview.narrative.*} de {@code messages_fr.properties} et {@code messages_en.properties}. Aucun
 * texte de phrase n'est figé en base (règle déjà prise pour les marqueurs de la tendance en M4-03).
 *
 * <p>Chaque fragment est un modèle {@link java.text.MessageFormat} (apostrophes doublées, pluriels
 * par {@code choice}). Une phrase est omise quand ses données manquent. Si un fragment manque dans
 * la langue demandée, la phrase entière est recomposée en français ; elle n'est omise que si le
 * français manque aussi. Aucune exception ne sort de la composition.
 *
 * <p>Seuils : la tendance « monte » (ou « baisse ») quand la moyenne des 30 derniers jours dépasse
 * (ou est inférieure à) celle des 30 jours précédents de plus de 1 point (strict) ; sinon « reste
 * stable ». Le vocabulaire de {@code overview.narrative.*} exclut trouble, diagnostic, risque et
 * pathologie (et leurs équivalents anglais) : le drapeau {@code risk} se dit « vigilance
 * particulière ».
 */
@Component
class NarrativeComposer {

  private static final String PREFIX = "overview.narrative.";
  private static final int MAX_LISTED_SITUATIONS = 3;

  private final MessageSource source;

  /** Source par défaut : les bundles {@code messages} de l'application, tous modèles formatés. */
  NarrativeComposer() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    s.setAlwaysUseMessageFormat(true);
    this.source = s;
  }

  /** Source fournie (tests : clé manquante simulée). Elle doit formater tous les modèles. */
  NarrativeComposer(MessageSource source) {
    this.source = source;
  }

  /** Une clé introuvable dans la langue essayée. */
  private static final class MissingFragmentException extends RuntimeException {
    MissingFragmentException(String key, Locale locale) {
      super("Fragment absent : " + key + " (" + locale + ")", null, false, false);
    }
  }

  static Locale normalize(Locale locale) {
    return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
  }

  /** Phrases du récit et point d'attention dans la locale demandée (français par défaut). */
  NarrativeDto compose(NarrativeFacts facts, Locale requested) {
    if (facts == null) {
      return new NarrativeDto(List.of(), null);
    }
    Locale locale = normalize(requested);
    List<String> sentences = new ArrayList<>();
    add(sentences, locale, l -> frame(facts, l));
    add(sentences, locale, l -> trend(facts, l));
    add(sentences, locale, l -> situations(facts, l));
    add(sentences, locale, l -> uncommentedEvent(facts, l));
    add(sentences, locale, l -> medications(facts, l));

    List<String> attention = new ArrayList<>();
    if (facts.flags() != null) {
      for (FlagFact flag : facts.flags()) {
        add(attention, locale, l -> flag(flag, l));
      }
    }
    return new NarrativeDto(sentences, attention.isEmpty() ? null : String.join(" ", attention));
  }

  /**
   * Ajoute la phrase si elle existe : langue demandée, sinon français, sinon rien. Un fragment
   * absent, mais aussi un modèle mal formé ({@code IllegalArgumentException}) ou une date
   * inattendue, font passer au repli puis à l'omission : une phrase ne doit jamais faire échouer la
   * vue d'ensemble.
   */
  private static void add(List<String> out, Locale locale, Function<Locale, String> builder) {
    String sentence = null;
    try {
      sentence = builder.apply(locale);
    } catch (RuntimeException e) {
      if (!Locale.FRENCH.equals(locale)) {
        try {
          sentence = builder.apply(Locale.FRENCH);
        } catch (RuntimeException ignored) {
          // fragment absent ou illisible aussi en français : la phrase est omise
        }
      }
    }
    if (sentence != null && !sentence.isBlank()) {
      out.add(sentence);
    }
  }

  // ---- phrases

  /** Phrase 1 : suivi depuis, nombre de séances, rythme (si au moins 3 séances sur 12 mois). */
  private String frame(NarrativeFacts facts, Locale l) {
    if (facts.followedSince() == null) {
      return null;
    }
    String count = msg("sessions", l, facts.sessionCount());
    String since = date(facts.followedSince(), l);
    String rhythm = facts.sessionCount() > 0 ? rhythm(facts.rhythmDays(), l) : null;
    return rhythm == null
        ? msg("frame", l, since, count)
        : msg("frame.rhythm", l, since, count, rhythm);
  }

  /**
   * Rythme : jusqu'à 10 jours une séance par semaine ; de 11 à 37 jours toutes les N semaines (N
   * arrondi) ; de 38 à 45 jours une par mois ; au-delà tous les N mois.
   */
  private String rhythm(Integer days, Locale l) {
    if (days == null || days <= 0) {
      return null;
    }
    if (days <= 10) {
      return msg("rhythm.weekly", l);
    }
    if (days <= 37) {
      return msg("rhythm.weeks", l, Math.round(days / 7.0f));
    }
    long months = Math.round(days / 30.44);
    return months <= 1 ? msg("rhythm.monthly", l) : msg("rhythm.months", l, months);
  }

  /** Phrase 2 : tendance, ou absence de recul, avec le ressenti dominant nommé. */
  private String trend(NarrativeFacts facts, Locale l) {
    Trend trend = facts.trend();
    if (trend == null) {
      return null;
    }
    if (trend.recentCount() == 0 || trend.recentMean() == null) {
      return msg("trend.none", l);
    }
    String dominant = dominantLabel(facts.dominant(), l);
    if (trend.previousCount() == 0 || trend.previousMean() == null || trend.direction() == null) {
      return msg("trend.single", l, dominant, trend.recentMean());
    }
    String key =
        switch (trend.direction()) {
          case UP -> "trend.up";
          case DOWN -> "trend.down";
          case STABLE -> "trend.stable";
        };
    return msg(key, l, dominant, trend.recentMean(), trend.previousMean());
  }

  private String dominantLabel(Dominant dominant, Locale l) {
    if (dominant == null) {
      return msg("dominant.unknown", l);
    }
    boolean english = Locale.ENGLISH.equals(l);
    String label =
        english && dominant.labelEn() != null && !dominant.labelEn().isBlank()
            ? dominant.labelEn()
            : dominant.labelFr();
    return label == null || label.isBlank() ? msg("dominant.unknown", l) : label;
  }

  /** Phrase 3 : situations ouvertes (aucune, une, deux ou trois, plus de trois). */
  private String situations(NarrativeFacts facts, Locale l) {
    List<String> titles = facts.situationTitles();
    if (titles == null || titles.isEmpty()) {
      return null;
    }
    if (titles.size() == 1) {
      return msg("situations.one", l, titles.get(0));
    }
    if (titles.size() <= MAX_LISTED_SITUATIONS) {
      return msg("situations.few", l, enumerate(titles, l));
    }
    String firstThree = String.join(", ", titles.subList(0, MAX_LISTED_SITUATIONS));
    return msg("situations.many", l, firstThree, titles.size() - MAX_LISTED_SITUATIONS);
  }

  /** « a et b », « a, b et c » (conjonction du fragment {@code and}). */
  private String enumerate(List<String> items, Locale l) {
    String and = msg("and", l);
    String head = String.join(", ", items.subList(0, items.size() - 1));
    return head + " " + and + " " + items.get(items.size() - 1);
  }

  /** Phrase 4 : événement récent que rien ne commente. */
  private String uncommentedEvent(NarrativeFacts facts, Locale l) {
    UncommentedEvent event = facts.uncommentedEvent();
    if (event == null || event.date() == null) {
      return null;
    }
    String date = date(event.date(), l);
    return event.title() == null || event.title().isBlank()
        ? msg("event.untitled", l, date)
        : msg("event", l, event.title(), date);
  }

  /** Phrase 5 : traitement en cours (le plus récent en tête, daté de son début). */
  private String medications(NarrativeFacts facts, Locale l) {
    List<MedicationFact> meds = facts.medications();
    if (meds == null || meds.isEmpty()) {
      return null;
    }
    List<String> labels =
        meds.stream()
            .map(
                m ->
                    m.dosage() == null || m.dosage().isBlank()
                        ? m.molecule()
                        : m.molecule() + " " + m.dosage())
            .toList();
    String since = meds.get(0).since() == null ? null : date(meds.get(0).since(), l);
    if (since == null) {
      return null;
    }
    return msg(
        meds.size() == 1 ? "medication.one" : "medication.many",
        l,
        String.join(", ", labels),
        since);
  }

  /** Point d'attention d'un drapeau ; nul pour un genre inconnu. */
  private String flag(FlagFact flag, Locale l) {
    if (flag.kind() == null || flag.since() == null) {
      return null;
    }
    boolean known =
        switch (flag.kind()) {
          case "watch", "risk", "shared_care" -> true;
          default -> false;
        };
    if (!known) {
      return null;
    }
    String base = "attention." + flag.kind();
    String date = date(flag.since(), l);
    boolean hasReason = flag.reason() != null && !flag.reason().isBlank();
    return hasReason ? msg(base + ".reason", l, date, flag.reason().strip()) : msg(base, l, date);
  }

  // ---- outils

  private static String date(LocalDate date, Locale l) {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(l).format(date);
  }

  /**
   * Fragment formaté dans la locale {@code l} ; lève {@link MissingFragmentException} si absent.
   */
  private String msg(String key, Locale l, Object... args) {
    String text = source.getMessage(PREFIX + key, args, null, l);
    if (text == null) {
      throw new MissingFragmentException(PREFIX + key, l);
    }
    return text;
  }
}
