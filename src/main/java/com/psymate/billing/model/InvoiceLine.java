package com.psymate.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Ligne d'une facture (table {@code invoice_line}, V18, L4) : désignation, date, quantité, prix
 * unitaire et montant en centimes entiers (G Q84). Une ligne de séance porte {@code
 * session_entry_id} et sa date ; sa désignation ne contient aucun texte clinique (G Q83 : « Séance
 * du 05/11/2026 » ou le libellé de la prestation et la date). Une remise est une ligne à prix
 * négatif. Les lignes d'un brouillon sont remplacées en bloc (PATCH {@code lines}) : supprimées
 * puis recréées, jamais modifiées sur place. {@code source_line_id} (ligne annulée par un avoir)
 * appartient à M15-11. Aucune association JPA : UUID nus.
 */
@Entity
@Table(name = "invoice_line")
public class InvoiceLine implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID invoiceId;

  @Column(nullable = false)
  private int position;

  private UUID serviceId;

  @Column(nullable = false)
  private String label;

  private LocalDate occurredOn;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false)
  private int unitPriceCents;

  @Column(nullable = false)
  private long amountCents;

  private UUID sessionEntryId;

  @Column(updatable = false)
  private UUID sourceLineId;

  @Transient private boolean isNew = true;

  protected InvoiceLine() {}

  /** Nouvelle ligne ; le montant vaut quantité × prix unitaire (L3.2). */
  public InvoiceLine(
      UUID id,
      UUID invoiceId,
      int position,
      UUID serviceId,
      String label,
      LocalDate occurredOn,
      int quantity,
      int unitPriceCents,
      UUID sessionEntryId) {
    this.id = Objects.requireNonNull(id);
    this.invoiceId = Objects.requireNonNull(invoiceId);
    this.position = position;
    this.serviceId = serviceId;
    this.label = Objects.requireNonNull(label);
    this.occurredOn = occurredOn;
    this.quantity = quantity;
    this.unitPriceCents = unitPriceCents;
    this.amountCents = InvoiceTotals.lineAmount(quantity, unitPriceCents);
    this.sessionEntryId = sessionEntryId;
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

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public int getPosition() {
    return position;
  }

  public UUID getServiceId() {
    return serviceId;
  }

  public String getLabel() {
    return label;
  }

  public LocalDate getOccurredOn() {
    return occurredOn;
  }

  public int getQuantity() {
    return quantity;
  }

  public int getUnitPriceCents() {
    return unitPriceCents;
  }

  public long getAmountCents() {
    return amountCents;
  }

  public UUID getSessionEntryId() {
    return sessionEntryId;
  }

  public UUID getSourceLineId() {
    return sourceLineId;
  }
}
