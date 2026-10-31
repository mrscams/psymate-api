package com.psymate.identity.auth;

import java.util.List;

/** Exceptions métier de l'authentification, traduites en RFC 9457 par AuthExceptionHandler. */
final class AuthExceptions {

  private AuthExceptions() {}

  static class InvalidCredentialsException extends RuntimeException {
    InvalidCredentialsException() {
      super("invalid credentials");
    }
  }

  static class InvalidTokenException extends RuntimeException {
    InvalidTokenException() {
      super("invalid or expired token");
    }
  }

  static class PasswordPolicyException extends RuntimeException {
    private final List<String> codes;

    PasswordPolicyException(List<String> codes) {
      super("password policy violated: " + codes);
      this.codes = codes;
    }

    List<String> codes() {
      return codes;
    }
  }

  /** Erreurs de validation par champ détectées par le service (adresse, téléphone). */
  static class FieldValidationException extends RuntimeException {
    private final List<String[]> errors;

    /** Chaque entrée : { champ, code, clé de message }. */
    FieldValidationException(List<String[]> errors) {
      super("invalid fields");
      this.errors = errors;
    }

    List<String[]> errors() {
      return errors;
    }
  }

  /** Clé e-mail + IP verrouillée (5 échecs, 15 min) : 423 avec Retry-After. */
  static class AccountLockedException extends RuntimeException {
    private final long retryAfterSeconds;

    AccountLockedException(long retryAfterSeconds) {
      super("account locked");
      this.retryAfterSeconds = retryAfterSeconds;
    }

    long retryAfterSeconds() {
      return retryAfterSeconds;
    }
  }

  static class EmailNotConfirmedException extends RuntimeException {
    EmailNotConfirmedException() {
      super("email not confirmed");
    }
  }

  static class AccountSuspendedException extends RuntimeException {
    AccountSuspendedException() {
      super("account suspended");
    }
  }

  /** Compte de bootstrap (M7-08) : le mot de passe à usage unique doit d'abord être remplacé. */
  static class PasswordChangeRequiredException extends RuntimeException {
    PasswordChangeRequiredException() {
      super("password change required");
    }
  }

  /** Le nouveau mot de passe est identique à l'actuel. */
  static class PasswordUnchangedException extends RuntimeException {
    PasswordUnchangedException() {
      super("new password equals current password");
    }
  }

  /** Code TOTP ou mot de passe faux lors de l'activation ou de la désactivation de la 2FA. */
  static class InvalidSecondFactorException extends RuntimeException {
    private final String field;

    InvalidSecondFactorException(String field) {
      super("invalid " + field);
      this.field = field;
    }

    String field() {
      return field;
    }
  }

  /** Opération 2FA incompatible avec l'état courant (déjà active, non initialisée). */
  static class TwoFactorStateException extends RuntimeException {
    private final String code;

    TwoFactorStateException(String code) {
      super(code);
      this.code = code;
    }

    String code() {
      return code;
    }
  }
}
