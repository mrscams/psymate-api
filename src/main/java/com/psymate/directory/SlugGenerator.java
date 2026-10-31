package com.psymate.directory;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Slug public « prénom-nom » : minuscules, sans accents ni titres, suffixe -2, -3… si collision.
 */
final class SlugGenerator {

  private static final Set<String> TITLES =
      Set.of("dr", "docteur", "pr", "professeur", "mme", "madame", "m", "mr", "monsieur");

  private SlugGenerator() {}

  /** Forme normalisée sans collision. */
  static String normalize(String displayName) {
    String s = Normalizer.normalize(displayName == null ? "" : displayName, Normalizer.Form.NFD);
    s = s.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace("œ", "oe").replace("æ", "ae");
    String[] words = s.split("[^a-z0-9]+");
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (String w : words) {
      if (w.isEmpty()) {
        continue;
      }
      boolean title = first && TITLES.contains(w) && words.length > 2;
      first = false;
      if (title) {
        continue; // « Dr Maya Kaddour » -> maya-kaddour
      }
      if (sb.length() > 0) {
        sb.append('-');
      }
      sb.append(w);
    }
    String base = sb.toString();
    if (base.length() > 80) {
      base = base.substring(0, 80).replaceAll("-+$", "");
    }
    return base.isEmpty() ? "praticien" : base;
  }

  /** Premier slug libre selon {@code taken} : base, base-2, base-3… */
  static String unique(String displayName, Predicate<String> taken) {
    String base = normalize(displayName);
    String candidate = base;
    for (int n = 2; taken.test(candidate); n++) {
      candidate = base + "-" + n;
    }
    return candidate;
  }
}
