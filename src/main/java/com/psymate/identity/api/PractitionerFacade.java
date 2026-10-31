package com.psymate.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Façade de lecture d'un praticien pour les autres modules. */
public interface PractitionerFacade {

  Optional<PractitionerView> find(UUID id);

  /** Numéro professionnel (ADELI / RPPS) déclaré, s'il existe. */
  Optional<String> professionalNumber(UUID id);

  /** Adresse e-mail du compte du praticien, si le praticien existe. */
  Optional<String> emailOf(UUID id);

  /**
   * Lecture par lot (un seul SELECT). Les identifiants inconnus sont absents de la map ; {@code
   * preferences} est vide dans cette vue allégée.
   */
  Map<UUID, PractitionerView> findAll(Collection<UUID> ids);

  /** Préférences effectives (valeurs stockées complétées par les défauts), vide si inconnu. */
  Map<String, Object> preferencesOf(UUID id);

  /** Vue minimale d'un praticien. {@code preferences} : clés plates (ADR-11). */
  record PractitionerView(
      UUID id,
      UUID tenantId,
      String profession,
      String verificationStatus,
      Map<String, Object> preferences) {}
}
