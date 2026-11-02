package com.psymate.vocabulary.model;

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
public class LinkType {

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

  public LinkType(
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

  public String getCode() {
    return code;
  }

  public boolean isDirected() {
    return directed;
  }

  public String getLabelFr() {
    return labelFr;
  }

  public String getLabelEn() {
    return labelEn;
  }

  public String getInverseLabelFr() {
    return inverseLabelFr;
  }

  public String getInverseLabelEn() {
    return inverseLabelEn;
  }

  public List<String> getAllowedSourceTypes() {
    return allowedSourceTypes;
  }

  public List<String> getAllowedTargetTypes() {
    return allowedTargetTypes;
  }

  public String getLineStyle() {
    return lineStyle;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
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
