package com.psymate.vocabulary.model;

import com.psymate.shared.time.Timestamps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * Tag libre d'un praticien, table {@code tag}. Interne au module vocabulary. {@code tenant_id},
 * {@code practitioner_id} et {@code merged_into} sont des UUID nus. {@code usage_count} n'est
 * jamais modifié par le dirty checking : son incrément se fait en SQL (fusion de tags, requête
 * native). {@code @DynamicUpdate} garantit qu'un renommage ou un archivage n'écrit que les colonnes
 * modifiées, donc n'écrase jamais un compteur incrémenté entre-temps.
 */
@Entity
@Table(name = "tag")
@DynamicUpdate
public class Tag implements Persistable<UUID> {

  @Id private UUID id;

  @Column(updatable = false)
  private UUID tenantId;

  @Column(updatable = false)
  private UUID practitionerId;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  private String normalizedLabel;

  /** Lu seulement côté entité : incrémenté en SQL à la fusion (voir TagNativeQueries). */
  @Column(nullable = false, insertable = false, updatable = false)
  private int usageCount;

  private UUID mergedInto;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  private OffsetDateTime archivedAt;

  @Transient private boolean isNew = true;

  protected Tag() {}

  public Tag(
      UUID id,
      UUID tenantId,
      UUID practitionerId,
      String label,
      String normalizedLabel,
      OffsetDateTime now) {
    this.id = Objects.requireNonNull(id);
    this.tenantId = tenantId;
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.label = Objects.requireNonNull(label);
    this.normalizedLabel = Objects.requireNonNull(normalizedLabel);
    this.usageCount = 0;
    this.createdAt = Timestamps.micros(now);
  }

  public void rename(String label, String normalizedLabel) {
    this.label = Objects.requireNonNull(label);
    this.normalizedLabel = Objects.requireNonNull(normalizedLabel);
  }

  /** Archive le tag fusionné : il libère son libellé (index partiel sur archived_at IS NULL). */
  public void archiveInto(UUID target, OffsetDateTime now) {
    this.mergedInto = Objects.requireNonNull(target);
    this.archivedAt = Timestamps.micros(now);
  }

  public boolean isArchived() {
    return archivedAt != null;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public String getLabel() {
    return label;
  }

  public String getNormalizedLabel() {
    return normalizedLabel;
  }

  public int getUsageCount() {
    return usageCount;
  }

  public UUID getMergedInto() {
    return mergedInto;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getArchivedAt() {
    return archivedAt;
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
    return this == o || (o instanceof Tag other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
