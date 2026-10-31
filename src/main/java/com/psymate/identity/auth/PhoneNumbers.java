package com.psymate.identity.auth;

import java.util.Optional;
import java.util.regex.Pattern;

/** Normalisation des numéros de téléphone en E.164 (France par défaut). */
final class PhoneNumbers {

  private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
  private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");
  private static final Pattern FRENCH_LOCAL = Pattern.compile("^0\\d{9}$");

  private PhoneNumbers() {}

  /** Numéro E.164, ou vide si la saisie n'est pas un numéro plausible. */
  static Optional<String> normalize(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String s = SEPARATORS.matcher(raw.trim()).replaceAll("");
    if (s.startsWith("00")) {
      s = "+" + s.substring(2);
    } else if (FRENCH_LOCAL.matcher(s).matches()) {
      s = "+33" + s.substring(1);
    }
    return E164.matcher(s).matches() ? Optional.of(s) : Optional.empty();
  }
}
