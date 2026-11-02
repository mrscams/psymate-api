package com.psymate.identity.auth.model;

import java.time.Instant;

/**
 * Double authentification d'un praticien. secret : chiffré (null = aucune 2FA) ; enabledAt : null
 * tant que l'activation n'est pas vérifiée.
 */
public record TotpState(byte[] secret, Instant enabledAt) {
  public boolean enabled() {
    return secret != null && enabledAt != null;
  }

  public boolean pending() {
    return secret != null && enabledAt == null;
  }
}
