package com.psymate.search;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extraits HTML des résultats. Tout texte issu du dossier est échappé ({@code & < > "}) avant que
 * les balises {@code <mark>} ne soient posées : le client peut injecter le résultat tel quel.
 * PostgreSQL surligne avec des délimiteurs neutres ({@link #OPEN}, {@link #CLOSE}) que cette classe
 * convertit ; {@code ts_headline} n'échappe rien lui-même.
 */
final class SnippetBuilder {

  /** Délimiteurs passés à {@code ts_headline} (StartSel / StopSel), retirés du texte avant. */
  static final String OPEN = "⟦";

  static final String CLOSE = "⟧";

  /** Séparateur de fragments de ts_headline (FragmentDelimiter par défaut). */
  private static final String FRAGMENT_DELIMITER = " ... ";

  private static final String ELLIPSIS = "…";
  private static final int PLAIN_MAX = 200;
  private static final int CONTEXT = 60;
  private static final double MIN_SIMILARITY = 0.2;
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
  private static final Pattern BLANKS = Pattern.compile("\\s+");

  private SnippetBuilder() {}

  static String escape(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '"' -> sb.append("&quot;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * Texte surligné par ts_headline (délimiteurs neutres) converti en HTML sûr ; null reste null.
   */
  static String fromMarked(String marked) {
    if (marked == null) {
      return null;
    }
    return escape(marked).replace(OPEN, "<mark>").replace(CLOSE, "</mark>");
  }

  /**
   * Surlignage d'un texte trouvé par ts_headline, en HTML sûr. {@code marked} est la sortie de
   * ts_headline sur le texte d'origine (accents conservés) ; {@code markedFolded} celle sur le
   * texte sans accents, avec lequel l'index et la requête ont travaillé : le radical français d'un
   * mot accentué (« journée ») diffère de celui du mot sans accent (« journee »), si bien que le
   * premier peut ne rien surligner. Dans ce cas, les repères du second sont reportés sur le texte
   * d'origine ; si l'alignement est impossible (le texte comporte des balises, des ligatures...),
   * le premier est renvoyé tel quel, sans surlignage.
   */
  static String highlight(String original, String marked, String markedFolded) {
    if (marked == null || marked.contains(OPEN) || original == null || markedFolded == null) {
      return fromMarked(marked);
    }
    String remapped = markedFolded.contains(OPEN) ? remap(original, markedFolded) : null;
    return fromMarked(remapped != null ? remapped : marked);
  }

  /**
   * Reporte les repères d'un extrait calculé sur le texte sans accents sur le texte d'origine.
   * Renvoie null si l'alignement échoue.
   */
  static String remap(String original, String markedFolded) {
    String stripped = stripAccents(original);
    StringBuilder out = new StringBuilder();
    int from = 0;
    String[] fragments = markedFolded.split(Pattern.quote(FRAGMENT_DELIMITER), -1);
    for (int f = 0; f < fragments.length; f++) {
      String fragment = fragments[f];
      String plain = fragment.replace(OPEN, "").replace(CLOSE, "");
      int at = stripped.indexOf(plain, from);
      if (at < 0) {
        return null;
      }
      String source = original.substring(at, at + plain.length());
      int p = 0;
      for (int i = 0; i < fragment.length(); i++) {
        String c = fragment.substring(i, i + 1);
        if (c.equals(OPEN) || c.equals(CLOSE)) {
          out.append(c);
        } else {
          out.append(source.charAt(p++));
        }
      }
      if (f < fragments.length - 1) {
        out.append(FRAGMENT_DELIMITER);
      }
      from = at + plain.length();
    }
    return out.toString();
  }

  /** Retire les accents de chaque caractère, sans changer la casse ni la longueur. */
  private static String stripAccents(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      sb.append(Normalizer.normalize(String.valueOf(s.charAt(i)), Normalizer.Form.NFD).charAt(0));
    }
    return sb.toString();
  }

  /** Début du texte, tronqué à environ 200 caractères sur une limite de mot ; null si vide. */
  static String plain(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String t = BLANKS.matcher(text.strip()).replaceAll(" ");
    if (t.length() <= PLAIN_MAX) {
      return escape(t);
    }
    int cut = t.lastIndexOf(' ', PLAIN_MAX);
    return escape(t.substring(0, cut > PLAIN_MAX / 2 ? cut : PLAIN_MAX)) + ELLIPSIS;
  }

  /**
   * Extrait d'un résultat approché : le mot du texte le plus proche (trigrammes) de la requête,
   * entouré de son contexte et surligné. À défaut de mot assez proche, le début du texte.
   */
  static String around(String body, String query) {
    if (body == null || body.isBlank()) {
      return null;
    }
    Set<String> queryWords = new HashSet<>();
    Matcher qm = WORD.matcher(fold(query));
    while (qm.find()) {
      queryWords.add(qm.group());
    }
    Matcher bm = WORD.matcher(fold(body));
    int bestStart = -1;
    int bestEnd = -1;
    double best = 0;
    while (bm.find()) {
      for (String q : queryWords) {
        double s = similarity(q, bm.group());
        if (s > best) {
          best = s;
          bestStart = bm.start();
          bestEnd = bm.end();
        }
      }
    }
    if (best < MIN_SIMILARITY) {
      return plain(body);
    }
    int start = Math.max(0, bestStart - CONTEXT);
    if (start > 0) {
      int space = body.indexOf(' ', start);
      start = space < 0 || space >= bestStart ? start : space + 1;
    }
    int end = Math.min(body.length(), bestEnd + CONTEXT);
    if (end < body.length()) {
      int space = body.lastIndexOf(' ', end);
      end = space <= bestEnd ? end : space;
    }
    return (start > 0 ? ELLIPSIS + " " : "")
        + escape(blanks(body.substring(start, bestStart)))
        + "<mark>"
        + escape(body.substring(bestStart, bestEnd))
        + "</mark>"
        + escape(blanks(body.substring(bestEnd, end)))
        + (end < body.length() ? " " + ELLIPSIS : "");
  }

  private static String blanks(String s) {
    return BLANKS.matcher(s).replaceAll(" ");
  }

  /** Minuscules sans accents, sans changer la longueur du texte (indices réutilisables). */
  static String fold(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      String decomposed = Normalizer.normalize(String.valueOf(s.charAt(i)), Normalizer.Form.NFD);
      sb.append(Character.toLowerCase(decomposed.charAt(0)));
    }
    return sb.toString();
  }

  /** Similarité de Jaccard des trigrammes, comme pg_trgm (mots entourés de deux espaces avant). */
  static double similarity(String a, String b) {
    Set<String> ga = trigrams(a);
    Set<String> gb = trigrams(b);
    if (ga.isEmpty() || gb.isEmpty()) {
      return 0;
    }
    Set<String> common = new HashSet<>(ga);
    common.retainAll(gb);
    int union = ga.size() + gb.size() - common.size();
    return (double) common.size() / union;
  }

  private static Set<String> trigrams(String word) {
    String padded = "  " + word + " ";
    Set<String> grams = new HashSet<>();
    for (int i = 0; i + 3 <= padded.length(); i++) {
      grams.add(padded.substring(i, i + 3));
    }
    return grams;
  }
}
