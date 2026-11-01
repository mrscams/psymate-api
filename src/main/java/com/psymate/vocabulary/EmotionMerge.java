package com.psymate.vocabulary;

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
 * Fusion de deux émotions, réversible jusqu'à {@code undo_until} (table {@code emotion_merge}).
 * Interne au module vocabulary. {@code practitioner_id}, {@code source_code} et {@code target_code}
 * sont des valeurs nues (pas d'association JPA). Les trois dates sont posées par le code Java (UTC,
 * microsecondes) : la base n'applique plus son DEFAULT {@code now()}.
 */
@Entity
@Table(name = "emotion_merge")
@DynamicUpdate
class EmotionMerge implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private String sourceCode;

  @Column(nullable = false, updatable = false)
  private String targetCode;

  @Column(nullable = false, updatable = false)
  private int affectedCount;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime mergedAt;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime undoUntil;

  private OffsetDateTime undoneAt;

  @Transient private boolean isNew = true;

  protected EmotionMerge() {}

  EmotionMerge(
      UUID id,
      UUID practitionerId,
      String sourceCode,
      String targetCode,
      int affectedCount,
      OffsetDateTime mergedAt,
      OffsetDateTime undoUntil) {
    this.id = Objects.requireNonNull(id);
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.sourceCode = Objects.requireNonNull(sourceCode);
    this.targetCode = Objects.requireNonNull(targetCode);
    this.affectedCount = affectedCount;
    this.mergedAt = Timestamps.micros(mergedAt);
    this.undoUntil = Timestamps.micros(undoUntil);
  }

  /** Fusion créée à {@code now}, annulable pendant {@code undoDays} jours. */
  static EmotionMerge open(
      UUID id,
      UUID practitionerId,
      String sourceCode,
      String targetCode,
      int affectedCount,
      OffsetDateTime now,
      int undoDays) {
    OffsetDateTime mergedAt = Timestamps.micros(now);
    return new EmotionMerge(
        id,
        practitionerId,
        sourceCode,
        targetCode,
        affectedCount,
        mergedAt,
        mergedAt.plusDays(undoDays));
  }

  boolean isUndone() {
    return undoneAt != null;
  }

  /** Encore annulable à cet instant : pas déjà annulée et {@code undo_until} non dépassé. */
  boolean isUndoableAt(OffsetDateTime now) {
    return undoneAt == null && undoUntil.isAfter(now);
  }

  void markUndone(OffsetDateTime now) {
    if (!isUndoableAt(now)) {
      throw new IllegalStateException("Fusion " + id + " non annulable");
    }
    this.undoneAt = Timestamps.micros(now);
  }

  @Override
  public UUID getId() {
    return id;
  }

  UUID getPractitionerId() {
    return practitionerId;
  }

  String getSourceCode() {
    return sourceCode;
  }

  String getTargetCode() {
    return targetCode;
  }

  int getAffectedCount() {
    return affectedCount;
  }

  OffsetDateTime getMergedAt() {
    return mergedAt;
  }

  OffsetDateTime getUndoUntil() {
    return undoUntil;
  }

  OffsetDateTime getUndoneAt() {
    return undoneAt;
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
    return this == o || (o instanceof EmotionMerge other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
