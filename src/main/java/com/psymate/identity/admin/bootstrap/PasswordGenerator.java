package com.psymate.identity.admin.bootstrap;

import java.security.SecureRandom;

/**
 * Mot de passe à usage unique du compte de bootstrap : 20 caractères tirés avec {@link
 * SecureRandom} dans les lettres (majuscules et minuscules) et les chiffres, avec au moins une
 * majuscule et une minuscule pour satisfaire la politique de mot de passe (12 caractères minimum,
 * casse mixte). Tirage par rejet : aucune position n'est biaisée.
 */
final class PasswordGenerator {

  static final int LENGTH = 20;
  static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
  static final String DIGITS = "0123456789";
  static final String ALPHABET = UPPER + LOWER + DIGITS;

  private final SecureRandom random;

  PasswordGenerator() {
    this(new SecureRandom());
  }

  PasswordGenerator(SecureRandom random) {
    this.random = random;
  }

  String generate() {
    while (true) {
      char[] chars = new char[LENGTH];
      boolean upper = false;
      boolean lower = false;
      for (int i = 0; i < LENGTH; i++) {
        char c = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        chars[i] = c;
        upper |= Character.isUpperCase(c);
        lower |= Character.isLowerCase(c);
      }
      if (upper && lower) {
        return new String(chars);
      }
    }
  }
}
