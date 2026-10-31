package com.psymate.identity.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/**
 * Compte suspendu manuellement par l'administration : sessions révoquées, fiche annuaire dépubliée.
 * Aucune donnée personnelle : seulement des identifiants et la profession.
 */
public class PractitionerSuspended extends DomainEvent {

  private UUID practitionerId;
  private String profession;

  /** Pour la désérialisation. */
  protected PractitionerSuspended() {}

  public PractitionerSuspended(UUID tenantId, UUID practitionerId, String profession) {
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
