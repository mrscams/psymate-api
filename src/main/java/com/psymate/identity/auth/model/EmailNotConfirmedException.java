package com.psymate.identity.auth.model;

public class EmailNotConfirmedException extends RuntimeException {
  public EmailNotConfirmedException() {
    super("email not confirmed");
  }
}
