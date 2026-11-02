package com.psymate.identity.auth.model;

public class InvalidTokenException extends RuntimeException {
  public InvalidTokenException() {
    super("invalid or expired token");
  }
}
