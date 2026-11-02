package com.psymate.identity.auth.model;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Politique de mot de passe (fiche 26) : au moins 12 caractères, majuscules et minuscules, absent
 * d'une liste locale de mots de passe compromis, sans e-mail ni nom. Aucun appel externe.
 */
public final class PasswordPolicy {

  static final int MIN_LENGTH = 12;
  static final String TOO_SHORT = "PASSWORD_TOO_SHORT";
  static final String CASE = "PASSWORD_CASE";
  static final String COMPROMISED = "PASSWORD_COMPROMISED";
  static final String PERSONAL = "PASSWORD_CONTAINS_PERSONAL_DATA";

  /** Variantes francophones et suffixées, absentes de la liste publique. */
  private static final Set<String> EXTRA =
      Set.of(
          "password2026!",
          "password1234!",
          "motdepasse123",
          "motdepasse1234",
          "azertyuiop123",
          "123456789012",
          "qwertyuiop12",
          "passwordpassword");

  /**
   * 10 000 mots de passe courants (security/common-passwords.txt, SecLists 10k-most-common, licence
   * MIT) plus EXTRA. Comparaison en minuscules.
   */
  private static final Set<String> COMPROMISED_LIST = loadCompromised();

  private static Set<String> loadCompromised() {
    Set<String> set = new HashSet<>(EXTRA);
    try (InputStream in =
            PasswordPolicy.class.getResourceAsStream("/security/common-passwords.txt");
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(Objects.requireNonNull(in), UTF_8))) {
      reader
          .lines()
          .map(l -> l.trim().toLowerCase(Locale.ROOT))
          .filter(l -> !l.isEmpty())
          .forEach(set::add);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Set.copyOf(set);
  }

  private PasswordPolicy() {}

  static int compromisedCount() {
    return COMPROMISED_LIST.size();
  }

  /** Codes de violation ; liste vide si le mot de passe est acceptable. */
  public static List<String> violations(
      String password, String email, String firstName, String lastName) {
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
