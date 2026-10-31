package com.psymate.identity.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Façade de lecture d'un praticien pour les autres modules. */
public interface PractitionerFacade {

  Optional<PractitionerView> find(UUID id);

  /** Vue minimale d'un praticien. {@code preferences} : clés plates (ADR-11). */
  record PractitionerView(
      UUID id,
      UUID tenantId,
      String profession,
      String verificationStatus,
      Map<String, Object> preferences) {}
}
