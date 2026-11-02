package com.psymate.identity.auth.model;

public class AccountSuspendedException extends RuntimeException {
  public AccountSuspendedException() {
    super("account suspended");
  }
}
