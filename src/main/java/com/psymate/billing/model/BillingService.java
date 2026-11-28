package com.psymate.billing.model;

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
import org.springframework.data.domain.Persistable;

/**
 * Prestation du praticien (table {@code billing_service}, V18, L4) : libellé, durée facultative,
 * prix en centimes d'euro entiers (G Q84). Au plus une prestation par défaut non archivée par
 * praticien (index {@code billing_service_one_default}). Une prestation ne se supprime jamais :
 * elle s'archive ({@code archived_at}). {@code tenant_id} et {@code practitioner_id} sont des UUID
 * nus : aucune association JPA.
 */
@Entity
@Table(name = "billing_service")
public class BillingService implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false)
  private String label;

  private Integer durationMin;

  /** Prix en centimes d'euro, strictement positif. */
  @Column(nullable = false)
  private int priceCents;

  @Column(nullable = false)
  private boolean isDefault;

  private OffsetDateTime archivedAt;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Transient private boolean isNew = true;

  protected BillingService() {}

  public BillingService(
      UUID id,
      UUID tenantId,
      UUID practitionerId,
      String label,
      Integer durationMin,
      int priceCents,
      boolean isDefault,
      OffsetDateTime now) {
    this.id = Objects.requireNonNull(id);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.label = Objects.requireNonNull(label);
    this.durationMin = durationMin;
    this.priceCents = priceCents;
    this.isDefault = isDefault;
    this.createdAt = Objects.requireNonNull(now);
    this.updatedAt = now;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
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

  public Integer getDurationMin() {
    return durationMin;
  }

  public int getPriceCents() {
    return priceCents;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public OffsetDateTime getArchivedAt() {
    return archivedAt;
  }

  public boolean isArchived() {
    return archivedAt != null;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void rename(String label, OffsetDateTime now) {
    this.label = Objects.requireNonNull(label);
    this.updatedAt = now;
  }

  public void changeDuration(Integer durationMin, OffsetDateTime now) {
    this.durationMin = durationMin;
    this.updatedAt = now;
  }

  public void changePrice(int priceCents, OffsetDateTime now) {
    this.priceCents = priceCents;
    this.updatedAt = now;
  }

  public void markDefault(boolean isDefault, OffsetDateTime now) {
    this.isDefault = isDefault;
    this.updatedAt = now;
  }

  /** Archive la prestation ; sans effet si elle l'est déjà. Elle perd son rôle de défaut. */
  public void archive(OffsetDateTime now) {
    if (archivedAt == null) {
      this.archivedAt = now;
      this.isDefault = false;
      this.updatedAt = now;
    }
  }
}
