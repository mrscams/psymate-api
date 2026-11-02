package com.psymate.identity.auth.model;

/** Clé e-mail + IP verrouillée (5 échecs, 15 min) : 423 avec Retry-After. */
public class AccountLockedException extends RuntimeException {
  private final long retryAfterSeconds;

  public AccountLockedException(long retryAfterSeconds) {
    super("account locked");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
