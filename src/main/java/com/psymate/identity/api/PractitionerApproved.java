package com.psymate.identity.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/**
 * Dossier professionnel approuvé par l'administration (statut approved). Aucune donnée personnelle
 * : seulement des identifiants et la profession.
 */
public class PractitionerApproved extends DomainEvent {

  private UUID practitionerId;
  private String profession;

  /** Pour la désérialisation. */
  protected PractitionerApproved() {}

  public PractitionerApproved(UUID tenantId, UUID practitionerId, String profession) {
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
