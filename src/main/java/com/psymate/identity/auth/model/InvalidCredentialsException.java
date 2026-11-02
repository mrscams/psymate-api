package com.psymate.identity.auth.model;

public class InvalidCredentialsException extends RuntimeException {
  public InvalidCredentialsException() {
    super("invalid credentials");
  }
}
