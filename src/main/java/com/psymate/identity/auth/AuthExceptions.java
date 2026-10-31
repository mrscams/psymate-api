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

  static class EmailAlreadyConfirmedException extends RuntimeException {
    EmailAlreadyConfirmedException() {
      super("email already confirmed");
    }
  }

  static class TooManyRequestsException extends RuntimeException {
    TooManyRequestsException() {
      super("too many requests");
    }
  }
}
