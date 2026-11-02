package com.psymate.patients.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Fiche patient (table {@code patient}). Interne au module patients : jamais exposée dans un DTO,
 * un événement ni l'API. {@code tenant_id} et {@code owner_id} sont des UUID nus (pas d'association
 * JPA). {@code clinical_rev} n'est jamais écrite par le dirty checking : son incrément atomique est
 * la requête {@code PatientJpaRepository.touchClinicalRev} (G Q30).
 */
@Entity
@Table(name = "patient")
public class Patient implements Persistable<UUID> {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private UUID ownerId;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  private String firstName;
  private String lastName;
  private LocalDate birthDate;
  private String pronoun;
  private String phone;
  private String email;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, Object> context;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, Object> followUp;

  @Column(nullable = false)
  private int currentVersion;

  /** Lue seulement : incrémentée en SQL par touchClinicalRev, jamais par un UPDATE d'entité. */
  @Column(insertable = false, updatable = false)
  private long clinicalRev;

  private OffsetDateTime archivedAt;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Transient private boolean isNew = true;

  protected Patient() {}

  public Patient(
      UUID id,
      UUID tenantId,
      UUID ownerId,
      String displayName,
      String firstName,
      String lastName,
      LocalDate birthDate,
      String pronoun,
      String phone,
      String email,
      Map<String, Object> context,
      Map<String, Object> followUp,
      OffsetDateTime now) {
    this.id = Objects.requireNonNull(id);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.ownerId = Objects.requireNonNull(ownerId);
    this.displayName = Objects.requireNonNull(displayName);
    this.firstName = firstName;
    this.lastName = lastName;
    this.birthDate = birthDate;
    this.pronoun = pronoun;
    this.phone = phone;
    this.email = email;
    this.context = copy(context);
    this.followUp = copy(followUp);
    this.currentVersion = 1;
    this.createdAt = micros(now);
    this.updatedAt = this.createdAt;
  }

  /** Horodatage courant en UTC, à la précision de PostgreSQL (microsecondes). */
  public static OffsetDateTime now() {
    return micros(OffsetDateTime.now(ZoneOffset.UTC));
  }

  private static OffsetDateTime micros(OffsetDateTime t) {
    return t.truncatedTo(ChronoUnit.MICROS);
  }

  private static Map<String, Object> copy(Map<String, Object> m) {
    return m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
  }

  /**
   * Remplace les colonnes éditables, incrémente la version courante et retourne la nouvelle version
   * (celle de l'instantané à archiver).
   */
  public int applyEdit(
      String displayName,
      String firstName,
      String lastName,
      LocalDate birthDate,
      String pronoun,
      String phone,
      String email,
      Map<String, Object> context,
      Map<String, Object> followUp,
      OffsetDateTime now) {
    this.displayName = Objects.requireNonNull(displayName);
    this.firstName = firstName;
    this.lastName = lastName;
    this.birthDate = birthDate;
    this.pronoun = pronoun;
    this.phone = phone;
    this.email = email;
    this.context = copy(context);
    this.followUp = copy(followUp);
    this.currentVersion++;
    this.updatedAt = micros(now);
    return currentVersion;
  }

  public void archive(OffsetDateTime now) {
    this.archivedAt = micros(now);
    this.updatedAt = this.archivedAt;
  }

  public void restore(OffsetDateTime now) {
    this.archivedAt = null;
    this.updatedAt = micros(now);
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public String getPronoun() {
    return pronoun;
  }

  public String getPhone() {
    return phone;
  }

  public String getEmail() {
    return email;
  }

  /** Vue de lecture : copiez-la avant de la sortir du module. */
  public Map<String, Object> getContext() {
    return context;
  }

  /** Vue de lecture : copiez-la avant de la sortir du module. */
  public Map<String, Object> getFollowUp() {
    return followUp;
  }

  public int getCurrentVersion() {
    return currentVersion;
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
    return this == o || (o instanceof Patient other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
