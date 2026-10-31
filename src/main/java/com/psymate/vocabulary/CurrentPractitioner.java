package com.psymate.vocabulary;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Accès au praticien courant, derrière le port (implémenté sur identity.api). */
@Component
class CurrentPractitioner {

  private final CurrentPractitionerPort port;

  CurrentPractitioner(CurrentPractitionerPort port) {
    this.port = port;
  }

  UUID id() {
    return port.practitionerId();
  }

  UUID tenantId() {
    return port.tenantId();
  }
}
