package com.psymate.identity.persistence;

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

/**
 * Jeton à usage unique (table {@code auth_token}) : confirmation d'e-mail, réinitialisation du mot
 * de passe. {@code payload} et {@code created_ip} (nullables) ne sont pas utilisés en V1 et donc
 * pas mappés. La consommation est atomique et passe par {@link AuthTokenNativeQueries#consume}.
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
    this.expiresAt = Times.utc(expiresAt);
    this.createdAt = Times.utc(now);
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
