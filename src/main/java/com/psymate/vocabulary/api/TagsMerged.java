package com.psymate.vocabulary.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/**
 * Tag fusionné dans un autre du même praticien (M9-03, revue ARCH-01) : partout où {@code
 * sourceTagId} est posé (entrées, patients), il est remplacé par {@code targetTagId}, sans doublon.
 * La source est archivée ({@code merged_into}) ; une fusion de tags ne s'annule pas. Publié dans la
 * transaction de la fusion ; chaque module propriétaire réécrit ses propres tables.
 */
public class TagsMerged extends DomainEvent {

  private UUID practitionerId;
  private UUID sourceTagId;
  private UUID targetTagId;

  /** Pour la désérialisation. */
  protected TagsMerged() {}

  public TagsMerged(UUID tenantId, UUID practitionerId, UUID sourceTagId, UUID targetTagId) {
    super(tenantId);
    this.practitionerId = practitionerId;
    this.sourceTagId = sourceTagId;
    this.targetTagId = targetTagId;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public UUID getSourceTagId() {
    return sourceTagId;
  }

  public UUID getTargetTagId() {
    return targetTagId;
  }
}
