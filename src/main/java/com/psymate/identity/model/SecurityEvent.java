package com.psymate.identity.model;

import com.psymate.shared.time.Timestamps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.net.InetAddress;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Journal de sécurité et d'administration (table {@code security_event}). Append-only : REVOKE
 * UPDATE, DELETE pour psymate_app, donc {@code @Immutable} (aucun UPDATE ni DELETE n'est jamais
 * émis). {@code user_agent} n'est jamais écrit.
 */
@Entity
@Immutable
@Table(name = "security_event")
public class SecurityEvent implements Persistable<UUID> {

  @Id private UUID id;

  private UUID practitionerId;
  private UUID actorId;

  @Column(nullable = false)
  private String kind;

  /** Colonne {@code inet} ; {@code InetAddress} est mappé nativement par Hibernate. */
  private InetAddress ip;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> details;

  @Column(nullable = false)
  private OffsetDateTime occurredAt;

  @Transient private boolean isNew = true;

  protected SecurityEvent() {}

  public SecurityEvent(
      UUID id,
      UUID practitionerId,
      UUID actorId,
      String kind,
      InetAddress ip,
      Map<String, Object> details,
      Instant occurredAt) {
    this.id = Objects.requireNonNull(id);
    this.practitionerId = practitionerId;
    this.actorId = actorId;
    this.kind = Objects.requireNonNull(kind);
    this.ip = ip;
    this.details = details == null ? null : new LinkedHashMap<>(details);
    this.occurredAt = Timestamps.utc(occurredAt);
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getKind() {
    return kind;
  }

  public InetAddress getIp() {
    return ip;
  }

  public Map<String, Object> getDetails() {
    return details;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
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
    return this == o || (o instanceof SecurityEvent other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
