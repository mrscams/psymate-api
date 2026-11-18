package com.psymate.patients.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Information du Profil 360 d'un patient (table {@code patient_profile_item}, G Q62 à Q70) : une
 * rubrique, un titre, un détail facultatif et, pour {@code history} seulement, une période. Elle
 * n'appartient à aucune séance (G Q63). Corrigée sur place, sans version (G Q67) ; retirée par
 * {@code archived_at}, jamais supprimée (G Q68). UUID nus vers le cabinet, le patient et le
 * praticien.
 */
@Entity
@Table(name = "patient_profile_item")
public class PatientProfileItem implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, updatable = false)
  private UUID patientId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PatientProfileCategory category;

  @Column(nullable = false)
  private String title;

  private String detail;

  private String periodLabel;

  private Short periodYear;

  @Column(nullable = false, updatable = false)
  private UUID createdBy;

  private OffsetDateTime archivedAt;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Transient private boolean isNew = true;

  protected PatientProfileItem() {}

  public PatientProfileItem(
      UUID id,
      UUID tenantId,
      UUID patientId,
      PatientProfileCategory category,
      String title,
      String detail,
      String periodLabel,
      Integer periodYear,
      UUID createdBy,
      OffsetDateTime now) {
    this.id = Objects.requireNonNull(id);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.patientId = Objects.requireNonNull(patientId);
    this.createdBy = Objects.requireNonNull(createdBy);
    this.createdAt = Objects.requireNonNull(now);
    this.updatedAt = now;
    assign(category, title, detail, periodLabel, periodYear);
  }

  /** Horodatage à la microseconde, comme PostgreSQL : ce qui est renvoyé est ce qui est relu. */
  public static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  /**
   * Correction sur place (G Q67) : remplace les cinq champs et avance {@code updated_at} si l'un
   * d'eux change. Renvoie vrai si la ligne a changé.
   */
  public boolean correct(
      PatientProfileCategory category,
      String title,
      String detail,
      String periodLabel,
      Integer periodYear,
      OffsetDateTime now) {
    boolean changed =
        this.category != category
            || !Objects.equals(this.title, title)
            || !Objects.equals(this.detail, detail)
            || !Objects.equals(this.periodLabel, periodLabel)
            || !Objects.equals(getPeriodYear(), periodYear);
    if (changed) {
      assign(category, title, detail, periodLabel, periodYear);
      this.updatedAt = Objects.requireNonNull(now);
    }
    return changed;
  }

  /** Retrait de la vue (G Q68) ; sans effet sur une information déjà retirée. */
  public void archive(OffsetDateTime now) {
    if (archivedAt == null) {
      this.archivedAt = Objects.requireNonNull(now);
    }
  }

  public boolean isArchived() {
    return archivedAt != null;
  }

  /**
   * Même règle que {@code ck_patient_profile_item_period} : une période hors {@code history} est un
   * défaut de programmation, la validation de l'API l'a refusée avant (422).
   */
  private void assign(
      PatientProfileCategory category,
      String title,
      String detail,
      String periodLabel,
      Integer periodYear) {
    Objects.requireNonNull(category);
    if (!category.hasPeriod() && (periodLabel != null || periodYear != null)) {
      throw new IllegalArgumentException("Période hors de la rubrique history.");
    }
    this.category = category;
    this.title = Objects.requireNonNull(title);
    this.detail = detail;
    this.periodLabel = periodLabel;
    this.periodYear = periodYear == null ? null : periodYear.shortValue();
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public PatientProfileCategory getCategory() {
    return category;
  }

  public String getTitle() {
    return title;
  }

  public String getDetail() {
    return detail;
  }

  public String getPeriodLabel() {
    return periodLabel;
  }

  public Integer getPeriodYear() {
    return periodYear == null ? null : periodYear.intValue();
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public OffsetDateTime getArchivedAt() {
    return archivedAt;
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
    return this == o
        || (o instanceof PatientProfileItem other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
