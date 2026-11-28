package com.psymate.billing.model;

/**
 * Numéro SIRET d'un établissement (M15-06, L5.1) : 14 chiffres dont le dernier est la clé de Luhn
 * de l'ensemble. La saisie admet des espaces (« 732 829 320 00074 »), retirés avant le contrôle. Le
 * cas particulier des établissements de La Poste (SIREN 356000000, contrôle par somme des chiffres)
 * n'est pas traité : il ne concerne pas un cabinet.
 */
public final class Siret {

  /** Nombre de chiffres d'un SIRET. */
  public static final int LENGTH = 14;

  private Siret() {}

  /** Saisie sans espaces ; null reste null. */
  public static String normalize(String raw) {
    return raw == null ? null : raw.replaceAll("\\s+", "");
  }

  /** Vrai si {@code value} (déjà normalisé) compte 14 chiffres et vérifie la clé de Luhn. */
  public static boolean isValid(String value) {
    if (value == null || value.length() != LENGTH) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return luhn(value);
  }

  /**
   * Clé de Luhn : en partant de la droite, un chiffre sur deux est doublé (moins 9 au-delà de 9) ;
   * la somme doit être un multiple de 10. {@code digits} ne contient que des chiffres.
   */
  static boolean luhn(String digits) {
    int sum = 0;
    boolean doubled = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int d = digits.charAt(i) - '0';
      if (doubled) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
      doubled = !doubled;
    }
    return sum % 10 == 0;
  }
}
