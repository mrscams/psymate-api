package com.psymate.events.api;

import java.time.Instant;
import java.util.UUID;

/** Classe de base des événements de domaine : identifiant, date (UTC) et cabinet concerné. */
public abstract class DomainEvent {

  private UUID id;
  private Instant occurredAt;
  private UUID tenantId;

  /** Pour la désérialisation Jackson. */
  protected DomainEvent() {}

  protected DomainEvent(UUID tenantId) {
    this.id = UUID.randomUUID();
    this.occurredAt = Instant.now();
    this.tenantId = tenantId;
  }

  public UUID getId() {
    return id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public UUID getTenantId() {
    return tenantId;
  }
}
