package com.psymate.identity.auth.model;

import java.util.List;

public class PasswordPolicyException extends RuntimeException {
  private final List<String> codes;

  public PasswordPolicyException(List<String> codes) {
    super("password policy violated: " + codes);
    this.codes = codes;
  }

  public List<String> codes() {
    return codes;
  }
}
