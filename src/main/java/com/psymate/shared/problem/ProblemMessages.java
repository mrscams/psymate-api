package com.psymate.shared.problem;

import com.psymate.shared.i18n.ServedLanguage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Textes des erreurs transverses (clés {@code error.http.*} de messages_fr.properties et
 * messages_en.properties). Source privée au package, comme dans les modules : le français est la
 * langue par défaut, l'anglais n'est servi que si le client le demande en premier dans {@code
 * Accept-Language} ({@link ServedLanguage#ofAcceptLanguage}). La locale du conteneur n'est
 * volontairement pas utilisée (MockMvc, serveurs en en_US : le défaut serait alors l'anglais).
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

  /**
   * Locale servie de la requête ({@link ServedLanguage#ofRequest}) : anglais si {@code
   * Accept-Language} le place en premier, arabe de même, sinon français.
   */
  public static Locale localeOf(HttpServletRequest request) {
    return ServedLanguage.ofRequest(request).locale();
  }

  /** Détail standard d'un code d'erreur ({@code error.http.<CODE>}). */
  public static String detail(String code, Locale locale, Object... args) {
    return SOURCE.getMessage(
        "error.http." + code, args, code, ServedLanguage.messageLocaleOf(locale));
  }

  /** Message d'un champ invalide quand la validation n'en fournit pas. */
  public static String invalidValue(Locale locale) {
    return SOURCE.getMessage(
        "error.http.INVALID_FIELD",
        null,
        "Valeur invalide.",
        ServedLanguage.messageLocaleOf(locale));
  }
}
