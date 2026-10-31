package com.psymate.identity.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/**
 * Praticien inscrit (compte créé, e-mail non confirmé). Aucune donnée personnelle : seulement des
 * identifiants et la profession.
 */
public class PractitionerRegistered extends DomainEvent {

  private UUID practitionerId;
  private String profession;

  /** Pour la désérialisation. */
  protected PractitionerRegistered() {}

  public PractitionerRegistered(UUID tenantId, UUID practitionerId, String profession) {
    super(tenantId);
    this.practitionerId = practitionerId;
    this.profession = profession;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public String getProfession() {
    return profession;
  }
}
