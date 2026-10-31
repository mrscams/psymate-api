package com.psymate.vocabulary;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Accès au praticien courant. Le port est résolu paresseusement : le contexte démarre sans
 * implémentation, une requête sans port échoue explicitement (jamais d'accès anonyme implicite).
 */
@Component
class CurrentPractitioner {

  private final ObjectProvider<CurrentPractitionerPort> port;

  CurrentPractitioner(ObjectProvider<CurrentPractitionerPort> port) {
    this.port = port;
  }

  UUID id() {
    return resolve().practitionerId();
  }

  UUID tenantId() {
    return resolve().tenantId();
  }

  private CurrentPractitionerPort resolve() {
    CurrentPractitionerPort p = port.getIfAvailable();
    if (p == null) {
      throw new IllegalStateException(
          "Aucun CurrentPractitionerPort : branchement sur identity.api attendu après M0-04");
    }
    return p;
  }
}
