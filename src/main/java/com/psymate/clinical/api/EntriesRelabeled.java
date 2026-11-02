package com.psymate.clinical.api;

import com.psymate.events.api.DomainEvent;
import java.util.List;
import java.util.UUID;

/**
 * Émotion ou tags d'entrées d'un patient réécrits après une fusion du vocabulaire (M9-03) : ni
 * correction ni nouvelle version, seulement le libellé porté. Un événement par patient touché,
 * publié après le décalage de {@code patient.clinical_rev}, pour que la recherche et la vue
 * d'ensemble se recalculent.
 */
public class EntriesRelabeled extends DomainEvent {

  private UUID patientId;
  private List<UUID> entryIds;

  /** Pour la désérialisation. */
  protected EntriesRelabeled() {}

  public EntriesRelabeled(UUID tenantId, UUID patientId, List<UUID> entryIds) {
    super(tenantId);
    this.patientId = patientId;
    this.entryIds = List.copyOf(entryIds);
  }

  public UUID getPatientId() {
    return patientId;
  }

  public List<UUID> getEntryIds() {
    return entryIds;
  }
}
