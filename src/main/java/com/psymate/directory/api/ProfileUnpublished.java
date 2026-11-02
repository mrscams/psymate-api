package com.psymate.directory.api;

import com.psymate.events.api.DomainEvent;
import com.psymate.shared.Reserved;
import java.util.UUID;

/**
 * Le profil public d'un praticien a été dépublié. Publié dès la V1 sans abonné, comme point
 * d'ancrage (G Q40).
 */
@Reserved(
    consumer = "V2, ou le premier abonné s'il vient avant",
    reason = "Publié dès la V1 comme point d'ancrage (A4.9)")
public class ProfileUnpublished extends DomainEvent {

  private UUID practitionerId;
  private String slug;

  /** Pour la désérialisation Jackson. */
  protected ProfileUnpublished() {}

  public ProfileUnpublished(UUID tenantId, UUID practitionerId, String slug) {
    super(tenantId);
    this.practitionerId = practitionerId;
    this.slug = slug;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public String getSlug() {
    return slug;
  }
}
