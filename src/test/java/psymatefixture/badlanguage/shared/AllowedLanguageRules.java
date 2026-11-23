package psymatefixture.badlanguage.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/** Les mêmes appels, dans shared : admis. */
public class AllowedLanguageRules {

  Locale english() {
    return Locale.ENGLISH;
  }

  Locale request(HttpServletRequest request) {
    return request.getLocale();
  }
}
