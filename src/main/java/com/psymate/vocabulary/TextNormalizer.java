package com.psymate.vocabulary;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalisation des libellés (tags et émotions) : même règle que {@code lower(f_unaccent(trim(x)))}
 * côté SQL, après suppression des '#'.
 */
public final class TextNormalizer {

  private TextNormalizer() {}

  public static String normalize(String input) {
    if (input == null) {
      return "";
    }
    String s = input.replace("#", "").trim();
    s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    s = s.toLowerCase(Locale.ROOT);
    // Lettres sans décomposition NFD, que unaccent de PostgreSQL translittère aussi.
    return s.replace("œ", "oe")
        .replace("æ", "ae")
        .replace("ß", "ss")
        .replace("ø", "o")
        .replace("ł", "l")
        .replace("đ", "d");
  }
}
