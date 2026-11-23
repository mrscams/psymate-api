package com.psymate.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Parité des fichiers de messages (M16-04, G Q99). Une clé lue sans message par défaut et absente
 * du fichier de la langue servie lèverait {@code NoSuchMessageException} : aucune source n'a de
 * fichier de base ni de repli sur la locale du système (M2.4 point 3). D'où une parité stricte :
 *
 * <ul>
 *   <li>{@code messages_en} porte exactement les clés de {@code messages_fr} ;
 *   <li>{@code messages_ar} aussi, sauf les deux familles du second temps, {@link
 *       #FRENCH_ONLY_IN_ARABIC}, qui en sont absentes (G Q102 : résumé « En bref » et export
 *       restent en français, M16-30 et M16-31) ;
 *   <li>chaque message garde les mêmes indices de paramètres {@code {n}} dans les trois langues ;
 *   <li>chaque message arabe se compile en {@code MessageFormat} quand le français se compile, et
 *       se met en forme en chiffres latins (G Q101).
 * </ul>
 */
class MessagesParityTest {

  /** Seules familles de clés absentes de messages_ar.properties (G Q99, Q102). */
  static final List<String> FRENCH_ONLY_IN_ARABIC =
      List.of("overview.narrative.", "clinical.export.");

  private static final Pattern ARGUMENT = Pattern.compile("\\{\\s*(\\d+)");

  /** Accolades qui ne commencent pas un argument : texte technique lu tel quel. */
  private static final Pattern LITERAL_BRACES = Pattern.compile("\\{(?!\\s*\\d)[^}]*\\}");

  private static final Pattern ARABIC_LETTER = Pattern.compile("[\\u0621-\\u064A]");
  private static final Pattern ARABIC_INDIC_DIGIT =
      Pattern.compile("[\\u0660-\\u0669\\u06F0-\\u06F9]");

  private static final Properties FR = load("fr");
  private static final Properties EN = load("en");
  private static final Properties AR = load("ar");

  private static Properties load(String language) {
    String name = "messages_" + language + ".properties";
    try (InputStream in = MessagesParityTest.class.getClassLoader().getResourceAsStream(name)) {
      assertThat(in).as(name + " dans le classpath").isNotNull();
      Properties p = new Properties();
      try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        p.load(reader);
      }
      return p;
    } catch (IOException e) {
      throw new IllegalStateException(name, e);
    }
  }

  private static boolean frenchOnly(String key) {
    return FRENCH_ONLY_IN_ARABIC.stream().anyMatch(key::startsWith);
  }

  private static Set<String> keys(Properties p) {
    return new TreeSet<>(p.stringPropertyNames());
  }

  private static Set<String> minus(Set<String> a, Set<String> b) {
    Set<String> r = new TreeSet<>(a);
    r.removeAll(b);
    return r;
  }

  private static Set<String> arguments(String message) {
    Set<String> r = new TreeSet<>();
    Matcher m = ARGUMENT.matcher(message);
    while (m.find()) {
      r.add(m.group(1));
    }
    return r;
  }

  private static boolean compiles(String message, Locale locale) {
    try {
      new MessageFormat(message, locale);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  @Test
  void englishHasExactlyTheFrenchKeys() {
    assertThat(minus(keys(FR), keys(EN)))
        .as("clés de messages_fr absentes de messages_en")
        .isEmpty();
    assertThat(minus(keys(EN), keys(FR)))
        .as("clés de messages_en absentes de messages_fr")
        .isEmpty();
  }

  @Test
  void arabicHasTheFrenchKeysExceptTheSecondTimeFamilies() {
    Set<String> expected = new TreeSet<>(keys(FR));
    expected.removeIf(MessagesParityTest::frenchOnly);
    assertThat(minus(expected, keys(AR)))
        .as("clés de messages_fr absentes de messages_ar")
        .isEmpty();
    assertThat(minus(keys(AR), expected))
        .as("clés de messages_ar absentes de messages_fr, ou d'une famille restée en français")
        .isEmpty();
  }

  @Test
  void theExceptedFamiliesExistInFrenchAndAreAbsentFromArabic() {
    for (String family : FRENCH_ONLY_IN_ARABIC) {
      assertThat(keys(FR)).as(family + "* en français").anyMatch(k -> k.startsWith(family));
      assertThat(keys(AR)).as(family + "* en arabe").noneMatch(k -> k.startsWith(family));
    }
  }

  @Test
  void theThreeLanguagesUseTheSameArguments() {
    List<String> mismatches = new ArrayList<>();
    for (String key : keys(FR)) {
      Set<String> french = arguments(FR.getProperty(key));
      if (EN.containsKey(key) && !arguments(EN.getProperty(key)).equals(french)) {
        mismatches.add("en " + key + " : " + arguments(EN.getProperty(key)) + " ≠ " + french);
      }
      if (AR.containsKey(key) && !arguments(AR.getProperty(key)).equals(french)) {
        mismatches.add("ar " + key + " : " + arguments(AR.getProperty(key)) + " ≠ " + french);
      }
    }
    assertThat(mismatches).as("indices {n} différents du français").isEmpty();
  }

  /**
   * Chaque message arabe se compile en MessageFormat, sauf un message à accolades littérales
   * ({@code {id}}, {@code {day, start, end}}, {@code {targetId, linkType}}) : celui-là est lu sans
   * MessageFormat (aucun argument), comme en français, et garde exactement les mêmes accolades que
   * le français.
   */
  @Test
  void arabicMessagesCompileOrKeepTheFrenchLiteralBraces() {
    List<String> broken = new ArrayList<>();
    for (String key : keys(AR)) {
      String ar = AR.getProperty(key);
      String fr = FR.getProperty(key);
      if (fr == null) {
        continue; // clé en trop : signalée par arabicHasTheFrenchKeysExceptTheSecondTimeFamilies
      }
      Set<String> literal = literalBraces(ar);
      if (!literal.equals(literalBraces(fr))) {
        broken.add(key + " : accolades littérales " + literal + " ≠ français");
      } else if (literal.isEmpty() && !compiles(ar, ServedLanguage.AR.locale())) {
        broken.add(key + " : ne se compile pas en MessageFormat");
      } else if (!literal.isEmpty() && !arguments(ar).isEmpty()) {
        broken.add(key + " : accolades littérales et arguments mêlés");
      }
    }
    assertThat(broken).isEmpty();
  }

  private static Set<String> literalBraces(String message) {
    Set<String> r = new TreeSet<>();
    Matcher m = LITERAL_BRACES.matcher(message);
    while (m.find()) {
      r.add(m.group());
    }
    return r;
  }

  /** G Q101 : un nombre passé en paramètre s'écrit en chiffres latins dans un message arabe. */
  @Test
  void arabicMessagesFormatNumbersWithLatinDigits() {
    List<String> eastern = new ArrayList<>();
    for (String key : keys(AR)) {
      String message = AR.getProperty(key);
      if (!compiles(message, ServedLanguage.AR.locale())) {
        continue;
      }
      Object[] args = new Object[10];
      Arrays.fill(args, 1234);
      String formatted = new MessageFormat(message, ServedLanguage.AR.locale()).format(args);
      if (ARABIC_INDIC_DIGIT.matcher(formatted).find()) {
        eastern.add(key + " → " + formatted);
      }
    }
    assertThat(eastern).as("chiffres arabo-indiens").isEmpty();
  }

  /**
   * Chaque message arabe est en arabe (sauf une valeur technique identique au français : nom du
   * produit, motif de date), ne contient ni chiffre arabo-indien ni apostrophe ASCII (le même texte
   * est lu avec ou sans MessageFormat selon la source), et garde « PsyMate » s'il est écrit en
   * français.
   */
  @Test
  void arabicMessagesAreArabicWithLatinTechnicalValues() {
    List<String> problems = new ArrayList<>();
    for (String key : keys(AR)) {
      String ar = AR.getProperty(key);
      String fr = FR.getProperty(key);
      if (!ARABIC_LETTER.matcher(ar).find() && !ar.equals(fr)) {
        problems.add(key + " : pas d'arabe");
      }
      if (ARABIC_INDIC_DIGIT.matcher(ar).find()) {
        problems.add(key + " : chiffre arabo-indien");
      }
      if (ar.indexOf('\'') >= 0) {
        problems.add(key + " : apostrophe");
      }
      if (fr != null && fr.contains("PsyMate") && !ar.contains("PsyMate")) {
        problems.add(key + " : PsyMate manquant");
      }
    }
    assertThat(problems).isEmpty();
  }
}
