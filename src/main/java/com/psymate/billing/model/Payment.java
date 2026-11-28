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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * Paiement ou remboursement (table {@code payment}, V18, L4 ; G Q90). Jamais supprimé (G Q95) : une
 * annulation pose {@code cancelled_at} (M15-10) ; aucun autre champ ne change après l'insertion.
 * Montant en centimes entiers, strictement positif (G Q84). M15-09 écrit le paiement d'une facture
 * émise « déjà réglée » ({@code alreadyPaid}) ; M15-10 les autres. Aucune association JPA : UUID
 * nus. {@code @DynamicUpdate} : l'annulation ne réécrit que {@code cancelled_at}.
 */
@Entity
@Table(name = "payment")
@DynamicUpdate
public class Payment implements Persistable<UUID> {

  /** Nature d'un paiement reçu ({@code payment.kind}). */
  public static final String KIND_PAYMENT = "payment";

  /** Mode toujours accepté, même absent des réglages (L5.4). */
  public static final String MODE_OTHER = "other";

  /** Modes de paiement ({@code payment.mode}, V18). */
  public static final List<String> MODES = List.of("transfer", "cheque", "cash", "card", "other");

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private UUID invoiceId;

  @Column(nullable = false, updatable = false)
  private String kind;

  @Column(nullable = false, updatable = false)
  private String mode;

  @Column(nullable = false, updatable = false)
  private long amountCents;

  @Column(nullable = false, updatable = false)
  private LocalDate receivedOn;

  @Column(updatable = false)
  private String reference;

  @Column(updatable = false)
  private String note;

  private OffsetDateTime cancelledAt;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Transient private boolean isNew = true;

  protected Payment() {}

  /** Nouveau paiement reçu sur une facture ({@code kind = payment}). */
  public static Payment received(
      UUID id,
      UUID tenantId,
      UUID practitionerId,
      UUID invoiceId,
      String mode,
      long amountCents,
      LocalDate receivedOn,
      String reference,
      OffsetDateTime now) {
    if (amountCents <= 0) {
      throw new IllegalArgumentException("montant d'un paiement strictement positif");
    }
    Payment p = new Payment();
    p.id = Objects.requireNonNull(id);
    p.tenantId = Objects.requireNonNull(tenantId);
    p.practitionerId = Objects.requireNonNull(practitionerId);
    p.invoiceId = Objects.requireNonNull(invoiceId);
    p.kind = KIND_PAYMENT;
    p.mode = Objects.requireNonNull(mode);
    p.amountCents = amountCents;
    p.receivedOn = Objects.requireNonNull(receivedOn);
    p.reference = reference;
    p.createdAt = Objects.requireNonNull(now);
    return p;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public String getKind() {
    return kind;
  }

  public String getMode() {
    return mode;
  }

  public long getAmountCents() {
    return amountCents;
  }

  public LocalDate getReceivedOn() {
    return receivedOn;
  }

  public String getReference() {
    return reference;
  }

  public String getNote() {
    return note;
  }

  public OffsetDateTime getCancelledAt() {
    return cancelledAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public boolean isCancelled() {
    return cancelledAt != null;
  }
}
