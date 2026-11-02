package com.psymate.identity.auth.model;

/** Recours (M1-14b) demandé par un compte qui n'est ni refusé ni suspendu : 409. */
public class AccountNotSuspendedException extends RuntimeException {
  public AccountNotSuspendedException() {
    super("account not suspended");
  }
}
