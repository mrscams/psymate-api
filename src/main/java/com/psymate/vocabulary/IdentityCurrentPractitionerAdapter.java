package com.psymate.vocabulary;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implémentation de production du port : le praticien courant est le principal de la session
 * (identity.api). Les endpoints de ce module sont protégés par la sécurité d'identity, donc une
 * requête sans principal est une erreur de configuration, jamais un accès anonyme toléré.
 */
@Component
class IdentityCurrentPractitionerAdapter implements CurrentPractitionerPort {

  private com.psymate.identity.api.CurrentPractitioner current() {
    return com.psymate.identity.api.CurrentPractitioner.get()
        .orElseThrow(() -> new IllegalStateException("Aucun praticien authentifié"));
  }

  @Override
  public UUID practitionerId() {
    return current().id();
  }

  @Override
  public UUID tenantId() {
    return current().tenantId();
  }
}
