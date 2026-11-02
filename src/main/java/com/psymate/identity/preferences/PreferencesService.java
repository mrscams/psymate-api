package com.psymate.identity.preferences;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.PreferencesChanged;
import com.psymate.identity.model.Practitioner;
import com.psymate.identity.repository.PractitionerJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PreferencesService {

  private final PractitionerJpaRepository practitioners;
  private final DomainEventPublisher events;

  PreferencesService(PractitionerJpaRepository practitioners, DomainEventPublisher events) {
    this.practitioners = practitioners;
    this.events = events;
  }

  @Transactional(readOnly = true)
  Map<String, Object> get(UUID practitionerId) {
    Practitioner p =
        practitioners.findById(practitionerId).orElseThrow(NoSuchElementException::new);
    return Preferences.effective(p.getProfession(), p.getPreferences());
  }

  @Transactional
  Map<String, Object> patch(UUID practitionerId, Map<String, Object> patch) {
    // Verrou de ligne pris à la première lecture du compte : sérialise les PATCH concurrents.
    Practitioner p =
        practitioners.findForUpdate(practitionerId).orElseThrow(NoSuchElementException::new);
    Preferences.MergeResult result =
        Preferences.merge(p.getPreferences(), patch == null ? Map.of() : patch);
    if (!result.violations().isEmpty()) {
      throw new InvalidPreferencesException(result.violations());
    }
    if (!result.changedKeys().isEmpty()) {
      p.replacePreferences(result.stored());
      events.publish(new PreferencesChanged(p.getTenantId(), practitionerId, result.changedKeys()));
    }
    return Preferences.effective(p.getProfession(), result.stored());
  }

  /** Valeurs hors domaine : rendu en 422 par PreferencesExceptionHandler. */
  static class InvalidPreferencesException extends RuntimeException {
    private final transient List<Preferences.Violation> violations;

    InvalidPreferencesException(List<Preferences.Violation> violations) {
      super("INVALID_PREFERENCES");
      this.violations = violations;
    }

    List<Preferences.Violation> violations() {
      return violations;
    }
  }
}
