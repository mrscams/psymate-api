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
 * Profil de facturation d'un patient (table {@code billing_patient}, V18, L4) : tarif en centimes
 * d'euro entiers (G Q84, Q87), adresse de facturation, tiers payeur. Une ligne par patient, créée à
 * la première écriture et modifiée sur place ; elle ne se supprime jamais (un profil effacé garde
 * sa ligne, champs à null). {@code patient_id}, {@code tenant_id} et {@code practitioner_id} sont
 * des UUID nus : aucune association JPA, aucune lecture de la table {@code patient}.
 */
@Entity
@Table(name = "billing_patient")
public class BillingPatient implements Persistable<UUID> {

  @Id private UUID patientId;

  @Column(nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private UUID practitionerId;

  /** Tarif du patient en centimes, strictement positif ; null si aucun. */
  private Integer rateCents;

  private String addressLines;

  private String thirdPartyName;

  private String thirdPartyAddress;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Transient private boolean isNew = true;

  protected BillingPatient() {}

  public BillingPatient(UUID patientId, UUID tenantId, UUID practitionerId) {
    this.patientId = Objects.requireNonNull(patientId);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.practitionerId = Objects.requireNonNull(practitionerId);
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  @Override
  public UUID getId() {
    return patientId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public Integer getRateCents() {
    return rateCents;
  }

  public String getAddressLines() {
    return addressLines;
  }

  public String getThirdPartyName() {
    return thirdPartyName;
  }

  public String getThirdPartyAddress() {
    return thirdPartyAddress;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Remplace tout le profil (PUT) : chaque champ null est effacé. Le praticien et le cabinet sont
   * ceux de la requête, propriétaires du patient.
   */
  public void replace(
      UUID tenantId,
      UUID practitionerId,
      Integer rateCents,
      String addressLines,
      String thirdPartyName,
      String thirdPartyAddress,
      OffsetDateTime now) {
    this.tenantId = Objects.requireNonNull(tenantId);
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.rateCents = rateCents;
    this.addressLines = addressLines;
    this.thirdPartyName = thirdPartyName;
    this.thirdPartyAddress = thirdPartyAddress;
    this.updatedAt = Objects.requireNonNull(now);
  }
}
