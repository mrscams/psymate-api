package com.psymate.identity.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Politique de mot de passe (fiche 26) : au moins 12 caractères, majuscules et minuscules, absent
 * d'une liste de mots de passe compromis, sans e-mail ni nom. Liste embarquée : la vérification
 * HIBP est hors périmètre (hors ligne tolérée).
 */
final class PasswordPolicy {

  static final int MIN_LENGTH = 12;
  static final String TOO_SHORT = "PASSWORD_TOO_SHORT";
  static final String CASE = "PASSWORD_CASE";
  static final String COMPROMISED = "PASSWORD_COMPROMISED";
  static final String PERSONAL = "PASSWORD_CONTAINS_PERSONAL_DATA";

  private static final Set<String> COMPROMISED_LIST =
      Set.of(
          "password2026!",
          "password1234!",
          "motdepasse123",
          "motdepasse1234",
          "azertyuiop123",
          "123456789012",
          "qwertyuiop12",
          "passwordpassword");

  private PasswordPolicy() {}

  /** Codes de violation ; liste vide si le mot de passe est acceptable. */
  static List<String> violations(String password, String email, String firstName, String lastName) {
    List<String> out = new ArrayList<>();
    if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
      out.add(TOO_SHORT);
      return out;
    }
    boolean lower = password.chars().anyMatch(Character::isLowerCase);
    boolean upper = password.chars().anyMatch(Character::isUpperCase);
    if (!lower || !upper) {
      out.add(CASE);
    }
    String lc = password.toLowerCase(Locale.ROOT);
    if (COMPROMISED_LIST.contains(lc)) {
      out.add(COMPROMISED);
    }
    if (containsPersonal(lc, email, firstName, lastName)) {
      out.add(PERSONAL);
    }
    return out;
  }

  private static boolean containsPersonal(String lc, String email, String first, String last) {
    List<String> parts = new ArrayList<>();
    if (email != null && !email.isBlank()) {
      String e = email.trim().toLowerCase(Locale.ROOT);
      parts.add(e);
      int at = e.indexOf('@');
      if (at > 0) {
        parts.add(e.substring(0, at));
      }
    }
    if (first != null) {
      parts.add(first.trim().toLowerCase(Locale.ROOT));
    }
    if (last != null) {
      parts.add(last.trim().toLowerCase(Locale.ROOT));
    }
    return parts.stream().filter(s -> s.length() >= 4).anyMatch(lc::contains);
  }
}
