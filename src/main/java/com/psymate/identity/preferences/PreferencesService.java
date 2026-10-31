package com.psymate.identity.preferences;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.PreferencesChanged;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class PreferencesService {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final PreferencesRepository repository;
  private final DomainEventPublisher events;
  private final JsonMapper json;

  PreferencesService(
      PreferencesRepository repository, DomainEventPublisher events, JsonMapper json) {
    this.repository = repository;
    this.events = events;
    this.json = json;
  }

  @Transactional(readOnly = true)
  Map<String, Object> get(UUID practitionerId) {
    PreferencesRepository.Row row =
        repository.findById(practitionerId).orElseThrow(NoSuchElementException::new);
    return Preferences.effective(row.profession(), parse(row.preferencesJson()));
  }

  @Transactional
  Map<String, Object> patch(UUID practitionerId, Map<String, Object> patch) {
    PreferencesRepository.Row row =
        repository.lockById(practitionerId).orElseThrow(NoSuchElementException::new);
    Preferences.MergeResult result =
        Preferences.merge(parse(row.preferencesJson()), patch == null ? Map.of() : patch);
    if (!result.violations().isEmpty()) {
      throw new InvalidPreferencesException(result.violations());
    }
    if (!result.changedKeys().isEmpty()) {
      repository.update(practitionerId, json.writeValueAsString(result.stored()));
      events.publish(new PreferencesChanged(row.tenantId(), practitionerId, result.changedKeys()));
    }
    return Preferences.effective(row.profession(), result.stored());
  }

  private Map<String, Object> parse(String raw) {
    return raw == null ? Map.of() : json.readValue(raw, MAP);
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
