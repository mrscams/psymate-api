package com.psymate.shared.problem;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Textes des erreurs transverses (clés {@code error.http.*} de messages_fr.properties et
 * messages_en.properties). Source privée au package, comme dans les modules : le français est la
 * langue par défaut, l'anglais n'est servi que si le client le demande en premier dans {@code
 * Accept-Language}. La locale du conteneur n'est volontairement pas utilisée (MockMvc, serveurs en
 * en_US : le défaut serait alors l'anglais).
 */
public final class ProblemMessages {

  private static final MessageSource SOURCE = source();

  private ProblemMessages() {}

  private static MessageSource source() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    return s;
  }

  /** Locale du message : anglais si {@code Accept-Language} le place en premier, sinon français. */
  public static Locale localeOf(HttpServletRequest request) {
    String header = request == null ? null : request.getHeader("Accept-Language");
    if (header == null || header.isBlank()) {
      return Locale.FRENCH;
    }
    try {
      List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
      if (!ranges.isEmpty() && ranges.get(0).getRange().startsWith("en")) {
        return Locale.ENGLISH;
      }
    } catch (IllegalArgumentException e) {
      // En-tête illisible : français.
    }
    return Locale.FRENCH;
  }

  /** Détail standard d'un code d'erreur ({@code error.http.<CODE>}). */
  public static String detail(String code, Locale locale, Object... args) {
    return SOURCE.getMessage("error.http." + code, args, code, locale);
  }

  /** Message d'un champ invalide quand la validation n'en fournit pas. */
  public static String invalidValue(Locale locale) {
    return SOURCE.getMessage("error.http.INVALID_FIELD", null, "Valeur invalide.", locale);
  }
}
