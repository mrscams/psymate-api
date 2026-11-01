package com.psymate.identity.preferences;

import com.psymate.identity.persistence.Practitioner;
import com.psymate.identity.persistence.PractitionerJpaRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PreferencesRepository {

  /** Cabinet, profession et préférences stockées d'un praticien (copie, jamais l'entité). */
  record Row(UUID tenantId, String profession, Map<String, Object> preferences) {}

  private final PractitionerJpaRepository practitioners;

  PreferencesRepository(PractitionerJpaRepository practitioners) {
    this.practitioners = practitioners;
  }

  /**
   * Lecture avec verrou de ligne : sérialise les PATCH concurrents d'un même compte. À appeler dans
   * une transaction, avant toute autre lecture du compte.
   */
  @Transactional
  Optional<Row> lockById(UUID id) {
    return practitioners.findForUpdate(id).map(PreferencesRepository::row);
  }

  Optional<Row> findById(UUID id) {
    return practitioners.findById(id).map(PreferencesRepository::row);
  }

  /** Remplace les préférences ; le compte est verrouillé par lockById dans la même transaction. */
  @Transactional
  void update(UUID id, Map<String, Object> preferences) {
    practitioners.findById(id).ifPresent(p -> p.replacePreferences(preferences));
  }

  private static Row row(Practitioner p) {
    return new Row(p.getTenantId(), p.getProfession(), new LinkedHashMap<>(p.getPreferences()));
  }
}
