package com.psymate.search.model;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntaxe du champ de recherche (écran 7) : {@code tag:travail}, {@code émotion:anxiété}, {@code
 * avant:2026-09}, {@code après:2026-06}, valeur éventuellement entre guillemets ({@code tag:"vie
 * pro"}). Classe pure, sans Spring.
 *
 * <p>Les jetons reconnus sont retirés du texte libre ; le préfixe est comparé sans tenir compte de
 * la casse ni des accents ({@code Émotion:}, {@code apres:}). Les autres guillemets restent dans le
 * texte, où {@code websearch_to_tsquery} les lit comme des expressions exactes. Les dates sont lues
 * en UTC : {@code avant:2026-03} donne « avant le 1er mars 2026 00:00Z », {@code après:2026-06}
 * donne « à partir du 1er juillet 2026 » (strictement après la période), {@code après:2026-06-15}
 * donne « à partir du 16 juin ».
 */
public final class QuerySyntax {

  /** Résultat de l'analyse ; {@code before} est exclusif, {@code after} inclusif. */
  public record Parsed(
      String text,
      List<String> tags,
      List<String> emotions,
      Instant before,
      Instant after,
      List<String> errors) {}

  private static final Pattern DATE = Pattern.compile("(\\d{4})(?:-(\\d{2})(?:-(\\d{2}))?)?");

  private QuerySyntax() {}

  public static Parsed parse(String q) {
    String s = q == null ? "" : q;
    StringBuilder text = new StringBuilder();
    List<String> tags = new ArrayList<>();
    List<String> emotions = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    Instant before = null;
    Instant after = null;
    int n = s.length();
    int i = 0;
    while (i < n) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        if (!text.isEmpty() && !Character.isWhitespace(text.charAt(text.length() - 1))) {
          text.append(' ');
        }
        i++;
      } else if (c == '"') {
        int end = closingQuote(s, i);
        text.append(s, i, end);
        i = end;
      } else {
        int j = i;
        while (j < n && !Character.isWhitespace(s.charAt(j)) && s.charAt(j) != '"') {
          j++;
        }
        String word = s.substring(i, j);
        int colon = word.indexOf(':');
        String kind = colon > 0 ? kindOf(fold(word.substring(0, colon))) : null;
        if (kind == null) {
          text.append(word);
          i = j;
          continue;
        }
        String value;
        if (colon == word.length() - 1 && j < n && s.charAt(j) == '"') {
          int end = closingQuote(s, j);
          boolean closed = end > j + 1 && s.charAt(end - 1) == '"';
          value = s.substring(j + 1, closed ? end - 1 : end);
          i = end;
        } else {
          value = word.substring(colon + 1);
          i = j;
        }
        value = value.strip();
        if (value.isEmpty()) {
          continue;
        }
        String token = word.substring(0, colon + 1) + value;
        switch (kind) {
          case "tag" -> tags.add(value);
          case "emotion" -> emotions.add(value);
          case "before" -> {
            Instant d = start(value);
            if (d == null) {
              errors.add(token);
            } else {
              before = before == null || d.isBefore(before) ? d : before;
            }
          }
          default -> {
            Instant d = endExclusive(value);
            if (d == null) {
              errors.add(token);
            } else {
              after = after == null || d.isAfter(after) ? d : after;
            }
          }
        }
      }
    }
    return new Parsed(
        text.toString().strip(),
        List.copyOf(tags),
        List.copyOf(emotions),
        before,
        after,
        List.copyOf(errors));
  }

  /** Index juste après le guillemet fermant (ou la fin de la chaîne si non fermé). */
  private static int closingQuote(String s, int open) {
    int close = s.indexOf('"', open + 1);
    return close < 0 ? s.length() : close + 1;
  }

  private static String kindOf(String foldedPrefix) {
    return switch (foldedPrefix) {
      case "tag" -> "tag";
      case "emotion" -> "emotion";
      case "avant", "before" -> "before";
      case "apres", "after" -> "after";
      default -> null;
    };
  }

  /** Minuscules et sans accents, pour comparer les préfixes. */
  static String fold(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
  }

  /**
   * Début de la période désignée (AAAA, AAAA-MM ou AAAA-MM-JJ), ou null si la date est invalide.
   */
  static Instant start(String value) {
    LocalDate d = periodStart(value);
    return d == null ? null : d.atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  /** Premier instant après la période désignée, ou null si la date est invalide. */
  static Instant endExclusive(String value) {
    Matcher m = DATE.matcher(value);
    LocalDate d = periodStart(value);
    if (d == null || !m.matches()) {
      return null;
    }
    LocalDate next =
        m.group(2) == null ? d.plusYears(1) : m.group(3) == null ? d.plusMonths(1) : d.plusDays(1);
    return next.atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  private static LocalDate periodStart(String value) {
    Matcher m = DATE.matcher(value);
    if (!m.matches()) {
      return null;
    }
    try {
      int year = Integer.parseInt(m.group(1));
      int month = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
      int day = m.group(3) == null ? 1 : Integer.parseInt(m.group(3));
      return LocalDate.of(year, month, day);
    } catch (DateTimeException | NumberFormatException e) {
      return null;
    }
  }
}
