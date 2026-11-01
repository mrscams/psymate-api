package com.psymate.seed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON minimal, sans dépendance : objets en {@code Map<String,Object>} (ordre conservé), tableaux
 * en {@code List<Object>}, {@code String}, {@code Long} ou {@code Double}, {@code Boolean}, null.
 */
public final class Json {

  private Json() {}

  /** Erreur de syntaxe JSON (message avec la position). */
  public static final class JsonException extends RuntimeException {
    public JsonException(String message) {
      super(message);
    }
  }

  // ---- lecture

  public static Object parse(String text) {
    Parser p = new Parser(text);
    p.skipSpaces();
    Object value = p.value();
    p.skipSpaces();
    if (p.pos < text.length()) {
      throw p.error("texte après la fin du document");
    }
    return value;
  }

  /** Lit un objet JSON ; une autre forme (ou un corps vide) donne une map vide. */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> parseObject(String text) {
    if (text == null || text.isBlank()) {
      return new LinkedHashMap<>();
    }
    Object value = parse(text);
    return value instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
  }

  private static final class Parser {
    final String s;
    int pos;

    Parser(String s) {
      this.s = s;
    }

    JsonException error(String what) {
      return new JsonException("JSON invalide à la position " + pos + " : " + what);
    }

    void skipSpaces() {
      while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
        pos++;
      }
    }

    Object value() {
      if (pos >= s.length()) {
        throw error("fin inattendue");
      }
      char c = s.charAt(pos);
      switch (c) {
        case '{':
          return object();
        case '[':
          return array();
        case '"':
          return string();
        case 't':
          return literal("true", Boolean.TRUE);
        case 'f':
          return literal("false", Boolean.FALSE);
        case 'n':
          return literal("null", null);
        default:
          if (c == '-' || (c >= '0' && c <= '9')) {
            return number();
          }
          throw error("caractère inattendu '" + c + "'");
      }
    }

    Object literal(String word, Object result) {
      if (!s.startsWith(word, pos)) {
        throw error("littéral inconnu");
      }
      pos += word.length();
      return result;
    }

    Map<String, Object> object() {
      Map<String, Object> map = new LinkedHashMap<>();
      pos++; // {
      skipSpaces();
      if (peek() == '}') {
        pos++;
        return map;
      }
      while (true) {
        skipSpaces();
        if (peek() != '"') {
          throw error("clé attendue");
        }
        String key = string();
        skipSpaces();
        expect(':');
        skipSpaces();
        map.put(key, value());
        skipSpaces();
        char c = next();
        if (c == '}') {
          return map;
        }
        if (c != ',') {
          pos--;
          throw error("',' ou '}' attendu");
        }
      }
    }

    List<Object> array() {
      List<Object> list = new ArrayList<>();
      pos++; // [
      skipSpaces();
      if (peek() == ']') {
        pos++;
        return list;
      }
      while (true) {
        skipSpaces();
        list.add(value());
        skipSpaces();
        char c = next();
        if (c == ']') {
          return list;
        }
        if (c != ',') {
          pos--;
          throw error("',' ou ']' attendu");
        }
      }
    }

    String string() {
      pos++; // guillemet ouvrant
      StringBuilder sb = new StringBuilder();
      while (true) {
        if (pos >= s.length()) {
          throw error("chaîne non terminée");
        }
        char c = s.charAt(pos++);
        if (c == '"') {
          return sb.toString();
        }
        if (c != '\\') {
          sb.append(c);
          continue;
        }
        char e = next();
        switch (e) {
          case '"', '\\', '/' -> sb.append(e);
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            if (pos + 4 > s.length()) {
              throw error("séquence \\u incomplète");
            }
            try {
              sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
            } catch (NumberFormatException ex) {
              throw error("séquence \\u invalide");
            }
            pos += 4;
          }
          default -> throw error("échappement inconnu \\" + e);
        }
      }
    }

    Object number() {
      int start = pos;
      if (peek() == '-') {
        pos++;
      }
      boolean decimal = false;
      while (pos < s.length()) {
        char c = s.charAt(pos);
        if (c >= '0' && c <= '9') {
          pos++;
        } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
          decimal = true;
          pos++;
        } else {
          break;
        }
      }
      String text = s.substring(start, pos);
      try {
        if (!decimal) {
          return Long.parseLong(text);
        }
        return Double.parseDouble(text);
      } catch (NumberFormatException ex) {
        pos = start;
        throw error("nombre invalide '" + text + "'");
      }
    }

    char peek() {
      return pos < s.length() ? s.charAt(pos) : '\0';
    }

    char next() {
      if (pos >= s.length()) {
        throw error("fin inattendue");
      }
      return s.charAt(pos++);
    }

    void expect(char c) {
      if (next() != c) {
        pos--;
        throw error("'" + c + "' attendu");
      }
    }
  }

  // ---- écriture

  public static String write(Object value) {
    StringBuilder sb = new StringBuilder();
    write(sb, value);
    return sb.toString();
  }

  private static void write(StringBuilder sb, Object value) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof String str) {
      writeString(sb, str);
    } else if (value instanceof Boolean b) {
      sb.append(b ? "true" : "false");
    } else if (value instanceof Double || value instanceof Float) {
      double d = ((Number) value).doubleValue();
      if (Double.isNaN(d) || Double.isInfinite(d)) {
        throw new JsonException("nombre non représentable en JSON : " + d);
      }
      sb.append(d);
    } else if (value instanceof Number n) {
      sb.append(n);
    } else if (value instanceof Map<?, ?> map) {
      sb.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> e : map.entrySet()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        writeString(sb, String.valueOf(e.getKey()));
        sb.append(':');
        write(sb, e.getValue());
      }
      sb.append('}');
    } else if (value instanceof Iterable<?> list) {
      sb.append('[');
      boolean first = true;
      for (Object o : list) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        write(sb, o);
      }
      sb.append(']');
    } else {
      // instants, dates, énumérations : leur représentation texte
      writeString(sb, value.toString());
    }
  }

  private static void writeString(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          // contrôles, et séparateurs de ligne unicode que certains lecteurs refusent
          if (c < 0x20 || c == 0x7f || c == 0x2028 || c == 0x2029) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }
}
