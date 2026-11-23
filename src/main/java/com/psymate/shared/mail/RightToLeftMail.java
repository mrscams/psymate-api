package com.psymate.shared.mail;

import com.psymate.shared.i18n.ServedLanguage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Corps d'un e-mail en arabe, de droite à gauche (G Q100) : le seul rendu de ce genre, appelé par
 * les cinq services d'envoi quand la langue du destinataire est l'arabe, et seulement alors. Les
 * e-mails en français et en anglais ne passent pas ici : ils restent un corps {@code text/plain}
 * seul, octet pour octet.
 *
 * <p>En arabe, l'e-mail part en {@code multipart/alternative} avec deux corps tirés du même texte
 * (celui des clés {@code *.mail.*} de {@code messages_ar.properties}) :
 *
 * <ul>
 *   <li>le texte brut ({@link #plainText}), où chaque ligne qui commence par un caractère latin ou
 *       un chiffre et contient de l'arabe reçoit une marque de droite à gauche (U+200F) : sans
 *       elle, le lecteur prendrait la ligne pour une ligne de gauche à droite ; une ligne sans
 *       arabe (un lien, une adresse, un texte saisi en français) reste telle quelle, pour qu'un
 *       lien copié ne porte aucun caractère invisible ;
 *   <li>un HTML minimal ({@link #html}), produit ici sans moteur de modèles : {@code <html
 *       lang="ar" dir="rtl">}, {@code <body dir="rtl">}, un paragraphe par bloc séparé d'une ligne
 *       vide, un {@code <br>} par saut de ligne ; tout le texte est échappé ; les liens {@code
 *       http(s)} deviennent des {@code <a dir="ltr">} cliquables ; les adresses e-mail et toute
 *       suite latine ou chiffrée (« PsyMate », noms saisis en lettres latines, dates, heures,
 *       fuseaux, durées) sont isolées dans un {@code <span dir="ltr">}, pour ne pas s'afficher à
 *       l'envers ; une ligne sans aucun caractère de droite à gauche est isolée entière dans un
 *       seul {@code <span dir="ltr">}, pour que sa ponctuation reste à sa place.
 * </ul>
 *
 * Les chiffres restent latins : le texte est mis en forme avec la locale servie {@code
 * ar-u-nu-latn} (G Q101) et ce rendu n'en convertit aucun. Rien n'est journalisé ici.
 */
public final class RightToLeftMail {

  /** Marque de droite à gauche (RIGHT-TO-LEFT MARK). */
  static final char RLM = '\u200F';

  /** Lien http(s) : jusqu'à un espace, un guillemet, un chevron ou une marque de direction. */
  private static final String URL = "https?://[^\\s<>\"'\\u200E\\u200F]+";

  /**
   * Adresse e-mail. Le lookbehind interdit de commencer au milieu d'une partie locale : sans lui,
   * {@code find()} relancerait la partie locale à chaque position d'une longue suite de {@code
   * %._+-} (temps quadratique sur une entrée hostile).
   */
  private static final String EMAIL =
      "(?<![A-Za-z0-9._%+\\-])[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)+";

  /**
   * Suite latine ou chiffrée : lettres latines (accentuées comprises) et chiffres latins, reliés
   * par des espaces ou des séparateurs ({@code . , : / _ + - ' @ #}), commençant et finissant par
   * une lettre ou un chiffre. « 09:00 », « Europe/Paris », « Claire Martin », « PsyMate ». Elle
   * s'arrête avant un lien, qui garde son propre rendu.
   */
  private static final String LATIN_RUN =
      "[\\p{IsLatin}0-9]+(?:[ \\u00A0.,:/_+\\-'’@#]+(?!https?://)[\\p{IsLatin}0-9]+)*";

  private static final Pattern INLINE =
      Pattern.compile("(?<url>" + URL + ")|(?<email>" + EMAIL + ")|(?<run>" + LATIN_RUN + ")");

  /** Ponctuation qui termine une phrase plutôt qu'un lien (retirée de la fin d'un lien). */
  private static final String URL_TRAILING = ".,;:!?)]}»،؛؟";

  private static final Pattern BLANK_LINES = Pattern.compile("\\n[ \\t\\u00A0]*\\n\\s*");

  private RightToLeftMail() {}

  /**
   * Vrai si un texte rédigé avec cette locale est en arabe : la langue servie est l'arabe et les
   * messages arabes existent ({@link ServedLanguage#messageLocaleOf}). Faux pour le français,
   * l'anglais, une locale absente ou inconnue.
   */
  public static boolean applies(Locale locale) {
    return ServedLanguage.of(ServedLanguage.messageLocaleOf(locale)) == ServedLanguage.AR;
  }

  /**
   * Pose le corps arabe d'un message déjà adressé : {@code multipart/alternative}, texte brut puis
   * HTML (l'ordre de préférence croissante de la RFC 2046), en UTF-8, avec {@code Content-Language:
   * ar}. L'objet, l'expéditeur et les destinataires ne sont pas touchés.
   */
  public static void setBody(MimeMessage message, String text) throws MessagingException {
    MimeBodyPart plain = new MimeBodyPart();
    plain.setText(plainText(text), "UTF-8", "plain");
    MimeBodyPart html = new MimeBodyPart();
    html.setText(html(text), "UTF-8", "html");
    MimeMultipart alternative = new MimeMultipart("alternative");
    alternative.addBodyPart(plain);
    alternative.addBodyPart(html);
    message.setContent(alternative);
    message.setHeader("Content-Language", "ar");
  }

  // ---- texte brut

  /**
   * Texte brut arabe : le texte, avec une marque U+200F en tête de chaque ligne dont le premier
   * caractère fort est latin ou un chiffre et qui contient de l'arabe.
   */
  public static String plainText(String text) {
    String normalized = normalize(text);
    String[] lines = normalized.split("\n", -1);
    StringBuilder out = new StringBuilder(normalized.length() + 16);
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        out.append('\n');
      }
      String line = lines[i];
      if (startsLeftToRight(line) && containsRightToLeft(line)) {
        out.append(RLM);
      }
      out.append(line);
    }
    return out.toString();
  }

  /** Premier caractère fort (ou chiffre) de la ligne : latin ou chiffre européen. */
  static boolean startsLeftToRight(String line) {
    for (int i = 0; i < line.length(); i++) {
      switch (Character.getDirectionality(line.charAt(i))) {
        case Character.DIRECTIONALITY_LEFT_TO_RIGHT, Character.DIRECTIONALITY_EUROPEAN_NUMBER -> {
          return true;
        }
        case Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_ARABIC_NUMBER -> {
          return false;
        }
        default -> {
          // Neutre ou faible : caractère suivant.
        }
      }
    }
    return false;
  }

  private static boolean containsRightToLeft(String line) {
    for (int i = 0; i < line.length(); i++) {
      byte d = Character.getDirectionality(line.charAt(i));
      if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT
          || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
        return true;
      }
    }
    return false;
  }

  // ---- HTML

  /**
   * HTML minimal de droite à gauche bâti à partir du texte brut (sans marque U+200F) : un
   * paragraphe par bloc séparé d'une ligne vide, un {@code <br>} par saut de ligne, texte échappé,
   * liens cliquables et suites latines isolés en {@code dir="ltr"}. Une ligne sans aucun caractère
   * de droite à gauche (texte saisi en français, motif latin, lien seul) est isolée entière dans un
   * seul {@code <span dir="ltr">}, ses liens rendus à l'intérieur : elle se lit de gauche à droite
   * avec sa ponctuation, comme dans le texte brut, qui la laisse telle quelle.
   */
  public static String html(String text) {
    StringBuilder out = new StringBuilder(normalize(text).length() * 2 + 400);
    out.append("<!DOCTYPE html>\n")
        .append("<html lang=\"ar\" dir=\"rtl\">\n")
        .append("<head>\n")
        .append("<meta charset=\"UTF-8\">\n")
        .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        .append("</head>\n")
        .append("<body dir=\"rtl\">\n")
        .append("<div dir=\"rtl\" lang=\"ar\" style=\"font-family: Tahoma, Arial, sans-serif;")
        .append(" font-size: 15px; line-height: 1.6; text-align: right;\">\n");
    String body = normalize(text).strip();
    if (!body.isEmpty()) {
      for (String paragraph : BLANK_LINES.split(body)) {
        out.append("<p>");
        String[] lines = paragraph.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
          if (i > 0) {
            out.append("<br>\n");
          }
          String line = lines[i];
          if (containsRightToLeft(line)) {
            inline(out, line, true);
          } else {
            out.append("<span dir=\"ltr\">");
            inline(out, line, false);
            out.append("</span>");
          }
        }
        out.append("</p>\n");
      }
    }
    return out.append("</div>\n</body>\n</html>\n").toString();
  }

  /**
   * Une ligne : liens cliquables ; avec {@code isolate}, adresses et suites latines isolées en
   * {@code dir="ltr"} ; le reste échappé tel quel.
   */
  private static void inline(StringBuilder out, String line, boolean isolate) {
    Matcher m = INLINE.matcher(line);
    int last = 0;
    while (m.find()) {
      int start = m.start();
      int end = m.end();
      out.append(escape(line.substring(last, start)));
      if (m.group("url") != null) {
        String url = withoutTrailingPunctuation(m.group("url"));
        end = start + url.length();
        if (url.indexOf("://") + 3 < url.length()) {
          String e = escape(url);
          out.append("<a href=\"").append(e).append("\" dir=\"ltr\">").append(e).append("</a>");
        } else {
          out.append(escape(url));
        }
      } else if (isolate) {
        out.append("<span dir=\"ltr\">").append(escape(m.group())).append("</span>");
      } else {
        out.append(escape(m.group()));
      }
      last = end;
      if (end < m.end()) {
        // Ponctuation retirée du lien : rendue comme texte, puis la recherche reprend après.
        m.region(end, line.length());
      }
    }
    out.append(escape(line.substring(last)));
  }

  /** Lien sans la ponctuation finale qui termine la phrase : boucle arrière, temps linéaire. */
  static String withoutTrailingPunctuation(String url) {
    int end = url.length();
    while (end > 0 && URL_TRAILING.indexOf(url.charAt(end - 1)) >= 0) {
      end--;
    }
    return url.substring(0, end);
  }

  /** Échappe {@code & < > " '} pour un texte ou une valeur d'attribut entre guillemets. */
  static String escape(String s) {
    StringBuilder out = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  private static String normalize(String text) {
    return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
  }
}
