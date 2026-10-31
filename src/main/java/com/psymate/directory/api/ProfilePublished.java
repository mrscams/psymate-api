package com.psymate.directory.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/** Le profil public d'un praticien a été publié. */
public class ProfilePublished extends DomainEvent {

  private UUID practitionerId;
  private String slug;

  /** Pour la désérialisation Jackson. */
  protected ProfilePublished() {}

  public ProfilePublished(UUID tenantId, UUID practitionerId, String slug) {
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
