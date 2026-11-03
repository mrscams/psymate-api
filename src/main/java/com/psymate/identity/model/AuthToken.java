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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Jeton à usage unique (table {@code auth_token}) : confirmation d'e-mail, réinitialisation du mot
 * de passe, changement d'e-mail (M10-04, {@code payload} = {@code {"newEmail": "…"}}). {@code
 * created_ip} (nullable) n'est pas utilisé et donc pas mappé. La consommation est atomique et passe
 * par {@link AuthTokenNativeQueries#consume} ou {@link AuthTokenNativeQueries#consumeWithPayload}.
 */
@Entity
@Table(name = "auth_token")
public class AuthToken implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private String kind;

  @Column(nullable = false, updatable = false)
  private String tokenHash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(updatable = false)
  private Map<String, Object> payload;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime expiresAt;

  private OffsetDateTime consumedAt;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Transient private boolean isNew = true;

  protected AuthToken() {}

  public AuthToken(
      UUID id, UUID practitionerId, String kind, String tokenHash, Instant expiresAt, Instant now) {
    this.id = Objects.requireNonNull(id);
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.kind = Objects.requireNonNull(kind);
    this.tokenHash = Objects.requireNonNull(tokenHash);
    this.expiresAt = Timestamps.utc(expiresAt);
    this.createdAt = Timestamps.utc(now);
  }

  /** Jeton portant une charge utile JSON (changement d'e-mail : la nouvelle adresse). */
  public AuthToken(
      UUID id,
      UUID practitionerId,
      String kind,
      String tokenHash,
      Map<String, Object> payload,
      Instant expiresAt,
      Instant now) {
    this(id, practitionerId, kind, tokenHash, expiresAt, now);
    this.payload = payload == null ? null : Map.copyOf(payload);
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public String getKind() {
    return kind;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getConsumedAt() {
    return consumedAt;
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
    return this == o || (o instanceof AuthToken other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
