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
 * Code de secours 2FA haché (table {@code totp_recovery_code}). Rien n'est supprimé : un code
 * utilisé ou retiré reçoit {@code used_at}. L'usage unique est garanti par l'UPDATE conditionnel
 * {@code TotpRecoveryCodeJpaRepository.consumeRecoveryCode}, jamais par l'entité.
 */
@Entity
@Table(name = "totp_recovery_code")
public class TotpRecoveryCode implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private String codeHash;

  private OffsetDateTime usedAt;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Transient private boolean isNew = true;

  protected TotpRecoveryCode() {}

  public TotpRecoveryCode(UUID id, UUID practitionerId, String codeHash, Instant now) {
    this.id = Objects.requireNonNull(id);
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.codeHash = Objects.requireNonNull(codeHash);
    this.createdAt = Times.utc(now);
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public String getCodeHash() {
    return codeHash;
  }

  public OffsetDateTime getUsedAt() {
    return usedAt;
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
    return this == o || (o instanceof TotpRecoveryCode other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
