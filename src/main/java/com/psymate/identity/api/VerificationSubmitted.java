package com.psymate.identity.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/**
 * Dossier professionnel soumis pour vérification (statut under_review). Aucune donnée personnelle :
 * seulement des identifiants et la profession.
 */
public class VerificationSubmitted extends DomainEvent {

  private UUID practitionerId;
  private String profession;

  /** Pour la désérialisation. */
  protected VerificationSubmitted() {}

  public VerificationSubmitted(UUID tenantId, UUID practitionerId, String profession) {
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
