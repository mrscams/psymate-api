package com.psymate.identity.persistence;

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
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Justificatif du dossier professionnel (table {@code verification_document}). Le fichier chiffré
 * vit hors base ({@code storage_key}) ; la ligne n'est jamais supprimée : retrait par le praticien
 * ({@code deleted_at}), purge du fichier ({@code purged_at}). {@code @DynamicUpdate} : le retrait,
 * l'examen par un administrateur et la purge sont des flux indépendants qui n'écrivent que leurs
 * colonnes.
 */
@Entity
@DynamicUpdate
@Table(name = "verification_document")
public class VerificationDocument implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private String purpose;

  @Column(nullable = false, updatable = false)
  private String type;

  @Column(nullable = false, updatable = false)
  private String title;

  @Column(updatable = false)
  private String issuer;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(updatable = false)
  private Integer year;

  @Column(nullable = false, updatable = false)
  private String storageKey;

  @Column(nullable = false, updatable = false)
  private String mime;

  @Column(nullable = false, updatable = false)
  private int sizeBytes;

  @Column(nullable = false, updatable = false)
  private String sha256;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime uploadedAt;

  @Column(nullable = false)
  private String reviewedStatus;

  private OffsetDateTime reviewedAt;
  private UUID reviewedBy;
  private OffsetDateTime purgeAfter;
  private OffsetDateTime purgedAt;
  private OffsetDateTime deletedAt;

  @Transient private boolean isNew = true;

  protected VerificationDocument() {}

  /** Justificatif déposé pour la vérification du compte (purpose {@code verification}). */
  public static VerificationDocument uploaded(
      UUID id,
      UUID practitionerId,
      String type,
      String title,
      String issuer,
      Integer year,
      String storageKey,
      String mime,
      int sizeBytes,
      String sha256,
      Instant now) {
    VerificationDocument d = new VerificationDocument();
    d.id = Objects.requireNonNull(id);
    d.practitionerId = Objects.requireNonNull(practitionerId);
    d.purpose = "verification";
    d.type = Objects.requireNonNull(type);
    d.title = Objects.requireNonNull(title);
    d.issuer = issuer;
    d.year = year;
    d.storageKey = Objects.requireNonNull(storageKey);
    d.mime = Objects.requireNonNull(mime);
    d.sizeBytes = sizeBytes;
    d.sha256 = Objects.requireNonNull(sha256);
    d.uploadedAt = Timestamps.utc(now);
    d.reviewedStatus = "pending";
    return d;
  }

  // --- Méthodes métier : une par écriture, mêmes colonnes que l'ancien SQL ---

  /** Retrait par le praticien : la ligne reste, le fichier est supprimé (purged_at posé). */
  public void markDeleted(Instant now) {
    OffsetDateTime at = Timestamps.utc(now);
    this.deletedAt = at;
    if (this.purgedAt == null) {
      this.purgedAt = at;
    }
  }

  /** Fichier purgé : seulement s'il ne l'était pas déjà (idempotent). */
  public void markPurged(Instant now) {
    if (this.purgedAt == null) {
      this.purgedAt = Timestamps.utc(now);
    }
  }

  /** Examen par un administrateur. */
  public void review(String status, UUID adminId, Instant now) {
    this.reviewedStatus = Objects.requireNonNull(status);
    this.reviewedAt = Timestamps.utc(now);
    this.reviewedBy = adminId;
  }

  // --- Lecture ---

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public String getPurpose() {
    return purpose;
  }

  public String getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getIssuer() {
    return issuer;
  }

  public Integer getYear() {
    return year;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getMime() {
    return mime;
  }

  public int getSizeBytes() {
    return sizeBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public OffsetDateTime getUploadedAt() {
    return uploadedAt;
  }

  public String getReviewedStatus() {
    return reviewedStatus;
  }

  public OffsetDateTime getReviewedAt() {
    return reviewedAt;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public OffsetDateTime getPurgeAfter() {
    return purgeAfter;
  }

  public OffsetDateTime getPurgedAt() {
    return purgedAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
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
    return this == o
        || (o instanceof VerificationDocument other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
