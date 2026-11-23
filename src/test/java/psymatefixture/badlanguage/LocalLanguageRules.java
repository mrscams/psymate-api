package psymatefixture.badlanguage;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/** Résolutions locales de la langue, hors de shared : chacune doit être signalée. */
public class LocalLanguageRules {

  Locale english() {
    return Locale.ENGLISH;
  }

  Locale french() {
    return Locale.FRENCH;
  }

  Locale tag(String tag) {
    return Locale.forLanguageTag(tag);
  }

  Object ranges(String header) {
    return Locale.LanguageRange.parse(header);
  }

  Locale request(HttpServletRequest request) {
    return request.getLocale();
  }

  Object requestLocales(HttpServletRequest request) {
    return request.getLocales();
  }
}
