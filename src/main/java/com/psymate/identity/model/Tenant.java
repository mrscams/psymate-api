package com.psymate.identity.model;

import com.psymate.shared.time.Timestamps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Cabinet (table {@code tenant}). Interne au module identity. */
@Entity
@Table(name = "tenant")
public class Tenant implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Transient private boolean isNew = true;

  protected Tenant() {}

  public Tenant(UUID id, String name, Instant now) {
    this.id = Objects.requireNonNull(id);
    this.name = Objects.requireNonNull(name);
    this.createdAt = Timestamps.utc(Objects.requireNonNull(now));
  }

  @Override
  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNew = false;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof Tenant other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
