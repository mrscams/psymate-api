package com.psymate.billing.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * IBAN (M15-06, G Q95) : contrôle du format et de la clé mod 97 (ISO 13616). La saisie est
 * normalisée avant tout contrôle : espaces retirés, lettres en majuscules. L'IBAN lui-même ne se
 * garde jamais en base ni dans une réponse ni dans un journal : seuls ses quatre derniers
 * caractères ({@link #last4}) vont en base. Cette classe ne journalise rien et ne met jamais la
 * valeur dans un message d'exception.
 */
public final class Iban {

  /**
   * Code pays (deux lettres), clé (deux chiffres), puis le BBAN alphanumérique ; 15 à 34 caractères
   * en tout (le plus court, la Norvège, en compte 15).
   */
  private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}");

  private Iban() {}

  /** Saisie sans espaces, en majuscules ; null reste null. */
  public static String normalize(String raw) {
    return raw == null ? null : raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
  }

  /** Vrai si {@code value} (déjà normalisé) a le format d'un IBAN et une clé mod 97 exacte. */
  public static boolean isValid(String value) {
    return value != null && FORMAT.matcher(value).matches() && mod97(value) == 1;
  }

  /** Quatre derniers caractères d'un IBAN valide (normalisé), seuls gardés en base. */
  public static String last4(String value) {
    return value.substring(value.length() - 4);
  }

  /**
   * Reste de la division par 97 de l'IBAN réarrangé : les quatre premiers caractères passent à la
   * fin, chaque lettre devient deux chiffres (A = 10 … Z = 35). Calcul par morceaux, sans grand
   * entier. {@code value} a déjà le format attendu.
   */
  static int mod97(String value) {
    String rearranged = value.substring(4) + value.substring(0, 4);
    int remainder = 0;
    for (int i = 0; i < rearranged.length(); i++) {
      char c = rearranged.charAt(i);
      if (c >= '0' && c <= '9') {
        remainder = (remainder * 10 + (c - '0')) % 97;
      } else {
        remainder = (remainder * 100 + (c - 'A' + 10)) % 97;
      }
    }
    return remainder;
  }
}
