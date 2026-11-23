package psymatefixture.badlanguage;

import java.util.Locale;

/** Locale.ROOT et Locale.of ne résolvent pas une langue servie : admis. */
public class LanguageAgnostic {

  String lower(String s) {
    return s.toLowerCase(Locale.ROOT);
  }

  String spoken(String code) {
    return Locale.of(code).getDisplayLanguage(Locale.ROOT);
  }
}
