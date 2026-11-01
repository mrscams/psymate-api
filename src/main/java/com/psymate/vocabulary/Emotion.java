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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Émotion globale (produit) ou personnalisée (praticien), table {@code emotion}. Interne au module
 * vocabulary : jamais exposée hors des services, qui la convertissent en EmotionView. {@code
 * tenant_id}, {@code practitioner_id} et {@code created_by} sont des UUID nus. {@code
 * vocabulary_version} (DEFAULT 1) et {@code promoted_to} (V2, NULL) ne sont pas mappés : l'INSERT
 * les omet et la base applique le défaut. {@code @DynamicUpdate} : un renommage n'écrit que les
 * colonnes modifiées et ne peut pas défaire une désactivation concurrente (fusion).
 */
@Entity
@Table(name = "emotion")
@DynamicUpdate
class Emotion implements Persistable<String> {

  static final String SCOPE_GLOBAL = "global";
  static final String SCOPE_CUSTOM = "custom";

  @Id private String code;

  @Column(nullable = false, updatable = false)
  private String scope;

  @Column(updatable = false)
  private UUID tenantId;

  @Column(updatable = false)
  private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private String family;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  private Integer intensityLevel;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  private Integer valence;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  private Integer arousal;

  @Column(nullable = false)
  private String labelFr;

  private String labelEn;

  @Column(nullable = false)
  private String labelNormalized;

  @Column(nullable = false)
  private boolean active;

  @Column(updatable = false)
  private UUID createdBy;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Transient private boolean isNew = true;

  protected Emotion() {}

  Emotion(
      String code,
      String scope,
      UUID tenantId,
      UUID practitionerId,
      String family,
      Integer intensityLevel,
      Integer valence,
      Integer arousal,
      String labelFr,
      String labelEn,
      String labelNormalized,
      UUID createdBy,
      OffsetDateTime now) {
    this.code = Objects.requireNonNull(code);
    this.scope = Objects.requireNonNull(scope);
    this.tenantId = tenantId;
    this.practitionerId = practitionerId;
    this.family = Objects.requireNonNull(family);
    this.intensityLevel = intensityLevel;
    this.valence = valence;
    this.arousal = arousal;
    this.labelFr = Objects.requireNonNull(labelFr);
    this.labelEn = labelEn;
    this.labelNormalized = Objects.requireNonNull(labelNormalized);
    this.active = true;
    this.createdBy = createdBy;
    this.createdAt = Timestamps.micros(now);
  }

  /** Émotion personnalisée du praticien : sans libellé anglais, intensité ni valence. */
  static Emotion custom(
      String code,
      UUID tenantId,
      UUID practitionerId,
      String family,
      String labelFr,
      String labelNormalized,
      OffsetDateTime now) {
    return new Emotion(
        code,
        SCOPE_CUSTOM,
        tenantId,
        Objects.requireNonNull(practitionerId),
        family,
        null,
        null,
        null,
        labelFr,
        null,
        labelNormalized,
        practitionerId,
        now);
  }

  void rename(String labelFr, String labelNormalized) {
    this.labelFr = Objects.requireNonNull(labelFr);
    this.labelNormalized = Objects.requireNonNull(labelNormalized);
  }

  void deactivate() {
    this.active = false;
  }

  void activate() {
    this.active = true;
  }

  /** Vrai pour une personnalisée appartenant à ce praticien (jamais pour une globale). */
  boolean isCustomOf(UUID practitioner) {
    return SCOPE_CUSTOM.equals(scope)
        && practitioner != null
        && practitioner.equals(practitionerId);
  }

  @Override
  public String getId() {
    return code;
  }

  String getCode() {
    return code;
  }

  String getScope() {
    return scope;
  }

  UUID getTenantId() {
    return tenantId;
  }

  UUID getPractitionerId() {
    return practitionerId;
  }

  String getFamily() {
    return family;
  }

  Integer getIntensityLevel() {
    return intensityLevel;
  }

  Integer getValence() {
    return valence;
  }

  Integer getArousal() {
    return arousal;
  }

  String getLabelFr() {
    return labelFr;
  }

  String getLabelEn() {
    return labelEn;
  }

  String getLabelNormalized() {
    return labelNormalized;
  }

  boolean isActive() {
    return active;
  }

  UUID getCreatedBy() {
    return createdBy;
  }

  OffsetDateTime getCreatedAt() {
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
    return this == o || (o instanceof Emotion other && code != null && code.equals(other.code));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(code);
  }
}
