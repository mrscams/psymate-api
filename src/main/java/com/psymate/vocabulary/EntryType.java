package com.psymate.vocabulary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Type d'entrée clinique (table {@code entry_type}). Catalogue de référence en lecture seule :
 * {@code @Immutable}, aucun UPDATE ni INSERT émis par l'application (le seed vient de Flyway).
 */
@Entity
@Table(name = "entry_type")
@Immutable
class EntryType {

  @Id private String code;

  @Column(nullable = false)
  private String labelFr;

  @Column(nullable = false)
  private String labelEn;

  private String detailTable;

  @Column(nullable = false)
  private boolean hasDuration;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean active;

  protected EntryType() {}

  EntryType(
      String code,
      String labelFr,
      String labelEn,
      String detailTable,
      boolean hasDuration,
      int sortOrder,
      boolean active) {
    this.code = Objects.requireNonNull(code);
    this.labelFr = Objects.requireNonNull(labelFr);
    this.labelEn = Objects.requireNonNull(labelEn);
    this.detailTable = detailTable;
    this.hasDuration = hasDuration;
    this.sortOrder = sortOrder;
    this.active = active;
  }

  String getCode() {
    return code;
  }

  String getLabelFr() {
    return labelFr;
  }

  String getLabelEn() {
    return labelEn;
  }

  String getDetailTable() {
    return detailTable;
  }

  boolean hasDuration() {
    return hasDuration;
  }

  int getSortOrder() {
    return sortOrder;
  }

  boolean isActive() {
    return active;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof EntryType other && code != null && code.equals(other.code));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(code);
  }
}
