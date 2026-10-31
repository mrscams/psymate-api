package com.psymate.identity.auth;

import java.util.List;

/** Exceptions métier de l'authentification, traduites en RFC 9457 par AuthExceptionHandler. */
final class AuthExceptions {

  private AuthExceptions() {}

  static class DuplicateEmailException extends RuntimeException {
    DuplicateEmailException() {
      super("email already registered");
    }
  }

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
}
