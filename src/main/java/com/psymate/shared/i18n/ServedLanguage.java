package com.psymate.shared.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;

/**
 * Langue servie par l'API : français, anglais ou arabe, le français par défaut (G Q96). C'est le
 * seul endroit où une source de langue devient une langue servie ; les modules ne comparent plus
 * eux-mêmes une locale à « en » (G Q96, M2.4 point 2).
 *
 * <p>Deux sortes de sources :
 *
 * <ul>
 *   <li>une requête, ou son en-tête {@code Accept-Language} : {@link #ofAcceptLanguage}, {@link
 *       #ofAcceptLanguageTag}, {@link #ofRequest}, {@link #ofRequestLocale} ; chacune reprend à
 *       l'identique la règle qu'appliquaient les classes qu'elle remplace, pour que les réponses
 *       {@code fr} et {@code en} ne changent pas ;
 *   <li>une étiquette enregistrée (colonne {@code practitioner.locale}, préférence {@code locale},
 *       {@code booking_request.locale}) : {@link #ofTag} ; ou une locale déjà résolue : {@link
 *       #of}.
 * </ul>
 *
 * Une source absente, illisible ou d'une langue non servie donne le français. Chaque e-mail garde
 * la source qu'il lit (G Q100) : cette classe convertit, elle ne choisit pas la source.
 *
 * <p>Deux locales par langue :
 *
 * <ul>
 *   <li>{@link #locale()}, la locale servie. Pour l'arabe, {@code ar-u-nu-latn} (G Q101) : avec
 *       {@code ar} seule, {@code MessageFormat} et {@code NumberFormat} écrivent des chiffres
 *       arabo-indiens ; l'extension ne change pas la recherche du fichier de messages ({@code ar},
 *       puis la racine) ;
 *   <li>{@link #messageLocale()}, la locale à donner à une source de messages. Aucune source n'a de
 *       fichier {@code messages.properties} de base, et toutes sont en {@code
 *       fallbackToSystemLocale(false)} : une locale arabe sans {@code messages_ar.properties} ne
 *       retomberait pas sur le français. Le repli est donc écrit ici : l'arabe n'est donné aux
 *       sources de messages que si ce fichier existe dans le classpath, le français sinon.
 * </ul>
 *
 * Le résumé « En bref » et l'export restent en français quand l'arabe est demandé (G Q102) : ils
 * passent par {@link #arabicAsFrench()}.
 */
public enum ServedLanguage {
  FR("fr", Locale.FRENCH),
  EN("en", Locale.ENGLISH),
  AR("ar", Locale.forLanguageTag("ar-u-nu-latn"));

  /** Fichier des messages arabes (M16-04) ; tant qu'il manque, l'arabe reçoit le français. */
  static final String ARABIC_MESSAGES = "messages_ar.properties";

  private static final boolean ARABIC_MESSAGES_PRESENT = present(ARABIC_MESSAGES);

  private final String tag;
  private final Locale locale;

  ServedLanguage(String tag, Locale locale) {
    this.tag = tag;
    this.locale = locale;
  }

  private static boolean present(String resource) {
    ClassLoader loader = ServedLanguage.class.getClassLoader();
    return loader != null && loader.getResource(resource) != null;
  }

  /** Étiquette enregistrée : {@code fr}, {@code en} ou {@code ar}. */
  public String tag() {
    return tag;
  }

  /**
   * Locale servie : {@link Locale#FRENCH}, {@link Locale#ENGLISH}, ou {@code ar-u-nu-latn} pour
   * l'arabe (chiffres latins, calendrier grégorien, G Q101).
   */
  public Locale locale() {
    return locale;
  }

  /**
   * Locale à donner à une source de messages et à la mise en forme qui accompagne ses textes : la
   * locale servie, sauf pour l'arabe tant que {@code messages_ar.properties} n'existe pas (le
   * français alors).
   */
  public Locale messageLocale() {
    return messageLocale(ARABIC_MESSAGES_PRESENT);
  }

  /** {@link #messageLocale()} selon la présence du fichier des messages arabes. */
  Locale messageLocale(boolean arabicMessagesPresent) {
    return this == AR && !arabicMessagesPresent ? FR.locale : locale;
  }

  /**
   * Langue d'un texte qui n'existe pas en arabe : le résumé « En bref » et l'export (G Q102), dont
   * les clés ne sont pas traduites. L'arabe devient le français ; le français et l'anglais restent.
   */
  public ServedLanguage arabicAsFrench() {
    return this == AR ? FR : this;
  }

  // ---- étiquettes enregistrées et locales déjà résolues

  /**
   * Langue d'une étiquette enregistrée ({@code practitioner.locale}, préférence {@code locale},
   * {@code booking_request.locale}) : {@code en} et {@code ar} exactement, le français sinon (null,
   * vide ou valeur inconnue).
   */
  public static ServedLanguage ofTag(String tag) {
    if (EN.tag.equals(tag)) {
      return EN;
    }
    if (AR.tag.equals(tag)) {
      return AR;
    }
    return FR;
  }

  /**
   * Langue d'une locale déjà résolue (locale de la requête, argument {@code Locale} d'un
   * contrôleur, locale servie transmise d'une couche à l'autre) : selon sa langue, le français
   * sinon.
   */
  public static ServedLanguage of(Locale locale) {
    if (locale == null) {
      return FR;
    }
    String language = locale.getLanguage();
    if (EN.tag.equals(language)) {
      return EN;
    }
    if (AR.tag.equals(language)) {
      return AR;
    }
    return FR;
  }

  /** {@code of(locale).messageLocale()} : la locale qu'attend une source de messages. */
  public static Locale messageLocaleOf(Locale locale) {
    return of(locale).messageLocale();
  }

  // ---- requêtes

  /**
   * Langue d'un en-tête {@code Accept-Language}, par son intervalle de plus haute qualité (premier
   * de {@link Locale.LanguageRange#parse}) : anglais si cet intervalle commence par {@code en},
   * arabe s'il vaut {@code ar} ou commence par {@code ar-}, français sinon, et pour un en-tête
   * absent, vide ou illisible. Règle des erreurs transverses ({@code ProblemMessages}), de l'export
   * et du Profil 360.
   */
  public static ServedLanguage ofAcceptLanguage(String header) {
    if (header == null || header.isBlank()) {
      return FR;
    }
    try {
      List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
      if (!ranges.isEmpty()) {
        String range = ranges.get(0).getRange();
        if (range.startsWith(EN.tag)) {
          return EN;
        }
        if (range.equals(AR.tag) || range.startsWith(AR.tag + "-")) {
          return AR;
        }
      }
    } catch (IllegalArgumentException e) {
      // En-tête illisible : français.
    }
    return FR;
  }

  /**
   * Langue d'un en-tête {@code Accept-Language} par la langue de l'étiquette de son intervalle de
   * plus haute qualité ({@link Locale#forLanguageTag}) ; français pour un en-tête absent, vide ou
   * illisible. Règle de l'inscription (langue enregistrée dans {@code practitioner.locale}). Elle
   * ne diffère de {@link #ofAcceptLanguage} que pour des étiquettes rares (codes de trois lettres
   * commençant par {@code en}, étiquettes historiques comme {@code i-default}) : les deux sont
   * gardées pour qu'aucune réponse {@code fr} ou {@code en} ne change.
   */
  public static ServedLanguage ofAcceptLanguageTag(String header) {
    if (header == null || header.isBlank()) {
      return FR;
    }
    try {
      List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
      return ranges.isEmpty() ? FR : of(Locale.forLanguageTag(ranges.get(0).getRange()));
    } catch (IllegalArgumentException e) {
      return FR;
    }
  }

  /** {@link #ofAcceptLanguage} sur l'en-tête de la requête ; français sans requête. */
  public static ServedLanguage ofRequest(HttpServletRequest request) {
    return ofAcceptLanguage(request == null ? null : request.getHeader("Accept-Language"));
  }

  /**
   * Langue d'une requête par la locale du conteneur ({@code request.getLocale()}), mais français
   * quand l'en-tête {@code Accept-Language} est absent ou vide (la locale du conteneur serait celle
   * du serveur, ou l'anglais sous MockMvc). Règle des classes de messages des modules (agenda,
   * clinical, linking, overview, search).
   */
  public static ServedLanguage ofRequestLocale(HttpServletRequest request) {
    String header = request.getHeader("Accept-Language");
    return header == null || header.isBlank() ? FR : of(request.getLocale());
  }
}
