package com.psymate.vocabulary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Type de lien et sa matrice source/cible (table {@code link_type}, colonnes {@code text[]}).
 * Catalogue de référence en lecture seule : {@code @Immutable}, le seed vient de Flyway.
 */
@Entity
@Table(name = "link_type")
@Immutable
class LinkType {

  @Id private String code;

  @Column(nullable = false)
  private boolean directed;

  @Column(nullable = false)
  private String labelFr;

  @Column(nullable = false)
  private String labelEn;

  private String inverseLabelFr;

  private String inverseLabelEn;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> allowedSourceTypes;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> allowedTargetTypes;

  @Column(nullable = false)
  private String lineStyle;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean active;

  protected LinkType() {}

  LinkType(
      String code,
      boolean directed,
      String labelFr,
      String labelEn,
      String inverseLabelFr,
      String inverseLabelEn,
      List<String> allowedSourceTypes,
      List<String> allowedTargetTypes,
      String lineStyle,
      int sortOrder,
      boolean active) {
    this.code = Objects.requireNonNull(code);
    this.directed = directed;
    this.labelFr = Objects.requireNonNull(labelFr);
    this.labelEn = Objects.requireNonNull(labelEn);
    this.inverseLabelFr = inverseLabelFr;
    this.inverseLabelEn = inverseLabelEn;
    this.allowedSourceTypes = List.copyOf(allowedSourceTypes);
    this.allowedTargetTypes = List.copyOf(allowedTargetTypes);
    this.lineStyle = Objects.requireNonNull(lineStyle);
    this.sortOrder = sortOrder;
    this.active = active;
  }

  String getCode() {
    return code;
  }

  boolean isDirected() {
    return directed;
  }

  String getLabelFr() {
    return labelFr;
  }

  String getLabelEn() {
    return labelEn;
  }

  String getInverseLabelFr() {
    return inverseLabelFr;
  }

  String getInverseLabelEn() {
    return inverseLabelEn;
  }

  List<String> getAllowedSourceTypes() {
    return allowedSourceTypes;
  }

  List<String> getAllowedTargetTypes() {
    return allowedTargetTypes;
  }

  String getLineStyle() {
    return lineStyle;
  }

  int getSortOrder() {
    return sortOrder;
  }

  boolean isActive() {
    return active;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof LinkType other && code != null && code.equals(other.code));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(code);
  }
}
