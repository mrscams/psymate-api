package com.psymate.identity.api;

import com.psymate.events.api.DomainEvent;
import com.psymate.shared.Reserved;
import java.util.UUID;

/**
 * Dossier professionnel soumis pour vérification (statut under_review). Aucune donnée personnelle :
 * seulement des identifiants et la profession.
 *
 * <p>Publié dès la V1 sans abonné, comme point d'ancrage (G Q40).
 */
@Reserved(
    consumer = "V2, ou le premier abonné s'il vient avant",
    reason = "Publié dès la V1 comme point d'ancrage (A4.9)")
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
