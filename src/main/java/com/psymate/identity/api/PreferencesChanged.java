package com.psymate.identity.api;

import com.psymate.events.api.DomainEvent;
import com.psymate.shared.Reserved;
import java.util.List;
import java.util.UUID;

/**
 * Préférences d'un praticien modifiées. Porte seulement les noms des clés changées, jamais leurs
 * valeurs.
 *
 * <p>Publié dès la V1 sans abonné, comme point d'ancrage (G Q40).
 */
@Reserved(
    consumer = "V2, ou le premier abonné s'il vient avant",
    reason = "Publié dès la V1 comme point d'ancrage (A4.9)")
public class PreferencesChanged extends DomainEvent {

  private UUID practitionerId;
  private List<String> changedKeys;

  /** Pour la désérialisation. */
  protected PreferencesChanged() {}

  public PreferencesChanged(UUID tenantId, UUID practitionerId, List<String> changedKeys) {
    super(tenantId);
    this.practitionerId = practitionerId;
    this.changedKeys = List.copyOf(changedKeys);
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public List<String> getChangedKeys() {
    return changedKeys;
  }
}
