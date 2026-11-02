package com.psymate.directory.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.data.domain.Persistable;

/**
 * Entrée de la liste fermée des spécialités et publics de l'annuaire (table {@code
 * directory_vocabulary}, G Q15). Aucune suppression : une entrée se désactive. Interne au module
 * directory.
 */
@Entity
@Table(name = "directory_vocabulary")
public class DirectoryVocabulary implements Persistable<DirectoryVocabularyId> {

  @EmbeddedId private DirectoryVocabularyId id;

  @Column(nullable = false)
  private String labelFr;

  private String labelEn;

  @Column(nullable = false)
  private short sortOrder;

  @Column(nullable = false)
  private boolean active;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Transient private boolean isNew = true;

  protected DirectoryVocabulary() {}

  /** Nouvelle entrée, active. {@code sortOrder} est borné à smallint par le service. */
  public DirectoryVocabulary(
      DirectoryVocabularyId id, String labelFr, String labelEn, int sortOrder, OffsetDateTime now) {
    this.id = Objects.requireNonNull(id);
    this.labelFr = Objects.requireNonNull(labelFr);
    this.labelEn = labelEn;
    this.sortOrder = (short) sortOrder;
    this.active = true;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void update(
      String labelFr, String labelEn, int sortOrder, boolean active, OffsetDateTime now) {
    this.labelFr = Objects.requireNonNull(labelFr);
    this.labelEn = labelEn;
    this.sortOrder = (short) sortOrder;
    this.active = active;
    this.updatedAt = now;
  }

  @Override
  public DirectoryVocabularyId getId() {
    return id;
  }

  public String getKind() {
    return id.getKind();
  }

  public String getCode() {
    return id.getCode();
  }

  public String getLabelFr() {
    return labelFr;
  }

  public String getLabelEn() {
    return labelEn;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
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
        || (o instanceof DirectoryVocabulary other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
