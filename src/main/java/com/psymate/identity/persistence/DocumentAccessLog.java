package com.psymate.identity.persistence;

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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * Ouverture d'un justificatif (table {@code document_access_log}). Append-only : REVOKE UPDATE,
 * DELETE pour psymate_app, donc {@code @Immutable} (INSERT seulement).
 */
@Entity
@Immutable
@Table(name = "document_access_log")
public class DocumentAccessLog implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false)
  private UUID documentId;

  @Column(nullable = false)
  private UUID adminId;

  /** Colonne {@code inet} ; {@code InetAddress} est mappé nativement par Hibernate. */
  private InetAddress ip;

  @Column(nullable = false)
  private OffsetDateTime accessedAt;

  @Transient private boolean isNew = true;

  protected DocumentAccessLog() {}

  public DocumentAccessLog(
      UUID id, UUID documentId, UUID adminId, InetAddress ip, Instant accessedAt) {
    this.id = Objects.requireNonNull(id);
    this.documentId = Objects.requireNonNull(documentId);
    this.adminId = Objects.requireNonNull(adminId);
    this.ip = ip;
    this.accessedAt = Timestamps.utc(accessedAt);
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public UUID getAdminId() {
    return adminId;
  }

  public InetAddress getIp() {
    return ip;
  }

  public OffsetDateTime getAccessedAt() {
    return accessedAt;
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
    return this == o || (o instanceof DocumentAccessLog other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
