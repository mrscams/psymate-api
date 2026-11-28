package com.psymate.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Séance à facturer (table {@code billable_session}, V18, L4 ; G Q86) : une séance de kind {@code
 * session} close, identifiée par l'entrée de la séance. États : {@code pending} (à facturer),
 * {@code dismissed} (« ne pas facturer », réversible), {@code invoiced} (dans une facture,
 * brouillon compris : {@code invoice_id} renseigné, M15-08 : {@link #attachTo}, {@link #release}).
 * La ligne ne porte aucun texte clinique : date, modalité, durée, prestation et montant choisis.
 * Tous les identifiants sont des UUID nus : aucune association JPA, aucune lecture d'une table de
 * clinical ou de patients. Une ligne naît par l'écoute de {@code SessionClosed} ou à la demande
 * (insertion idempotente du fragment natif) ; elle ne se supprime jamais.
 */
@Entity
@Table(name = "billable_session")
public class BillableSession implements Persistable<UUID> {

  public static final String PENDING = "pending";
  public static final String DISMISSED = "dismissed";
  public static final String INVOICED = "invoiced";

  @Id private UUID sessionEntryId;

  @Column(nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private UUID patientId;

  @Column(nullable = false, updatable = false)
  private LocalDate occurredOn;

  @Column(nullable = false, updatable = false)
  private String modality;

  @Column(updatable = false)
  private Integer durationMin;

  @Column(nullable = false)
  private String state;

  /** Prestation choisie pour cette séance ; null = prestation par défaut. */
  private UUID serviceId;

  /** Montant fixé sur la séance en centimes, strictement positif ; null = tarif calculé. */
  private Integer amountCents;

  private UUID invoiceId;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Transient private boolean isNew = true;

  protected BillableSession() {}

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  @Override
  public UUID getId() {
    return sessionEntryId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public UUID getSessionEntryId() {
    return sessionEntryId;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public LocalDate getOccurredOn() {
    return occurredOn;
  }

  public String getModality() {
    return modality;
  }

  public Integer getDurationMin() {
    return durationMin;
  }

  public String getState() {
    return state;
  }

  public UUID getServiceId() {
    return serviceId;
  }

  public Integer getAmountCents() {
    return amountCents;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public boolean isPending() {
    return PENDING.equals(state);
  }

  public boolean isDismissed() {
    return DISMISSED.equals(state);
  }

  public boolean isInvoiced() {
    return INVOICED.equals(state);
  }

  /** « Ne pas facturer » : pending → dismissed ; sans effet si elle l'est déjà. */
  public void dismiss(OffsetDateTime now) {
    requireNotInvoiced();
    if (!isDismissed()) {
      this.state = DISMISSED;
      this.updatedAt = Objects.requireNonNull(now);
    }
  }

  /** « Remettre à facturer » : dismissed → pending ; sans effet si elle l'est déjà. */
  public void restore(OffsetDateTime now) {
    requireNotInvoiced();
    if (!isPending()) {
      this.state = PENDING;
      this.updatedAt = Objects.requireNonNull(now);
    }
  }

  /** Montant fixé sur la séance ; null rend le tarif calculé (G Q87). */
  public void changeAmount(Integer amountCents, OffsetDateTime now) {
    requireNotInvoiced();
    this.amountCents = amountCents;
    this.updatedAt = Objects.requireNonNull(now);
  }

  /** Prestation de la séance ; null revient à la prestation par défaut. */
  public void changeService(UUID serviceId, OffsetDateTime now) {
    requireNotInvoiced();
    this.serviceId = serviceId;
    this.updatedAt = Objects.requireNonNull(now);
  }

  /**
   * Mise en facture (L3.1) : pending ou dismissed → invoiced, rattachée au brouillon. Sans effet si
   * elle est déjà dans ce brouillon ; 409 SESSION_ALREADY_INVOICED (avec {@code sessionEntryId}) si
   * elle est dans une autre facture.
   */
  public void attachTo(UUID invoiceId, OffsetDateTime now) {
    Objects.requireNonNull(invoiceId);
    if (isInvoiced()) {
      if (invoiceId.equals(this.invoiceId)) {
        return;
      }
      throw BillingException.sessionAlreadyInvoiced(sessionEntryId);
    }
    this.state = INVOICED;
    this.invoiceId = invoiceId;
    this.updatedAt = Objects.requireNonNull(now);
  }

  /**
   * Retour à facturer (L3.1, G Q86) : ligne retirée d'un brouillon ou brouillon supprimé → pending,
   * jamais dismissed ; sans effet si elle n'est pas facturée.
   */
  public void release(OffsetDateTime now) {
    if (isInvoiced()) {
      this.state = PENDING;
      this.invoiceId = null;
      this.updatedAt = Objects.requireNonNull(now);
    }
  }

  private void requireNotInvoiced() {
    if (isInvoiced()) {
      throw BillingException.sessionAlreadyInvoiced();
    }
  }
}
