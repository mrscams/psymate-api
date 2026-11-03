package com.psymate.identity.auth.model;

/** Session à révoquer inconnue, expirée ou appartenant à un autre compte (M10-06) : 404. */
public class SessionNotFoundException extends RuntimeException {
  public SessionNotFoundException() {
    super("session not found");
  }
}
