package com.psymate.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Événement de l'historique d'une facture (table {@code invoice_event}, V18, L4) : append-only
 * ({@code @Immutable}, E3.15) ; il ne se modifie jamais. Il ne disparaît qu'avec son brouillon
 * ({@code on delete cascade}, E9) ; celui d'une facture émise reste à jamais (G Q95). {@code
 * detail} ne porte ni nom de patient, ni montant, ni adresse e-mail hors des cas prévus (M15-11 :
 * adresse d'envoi). M15-08 écrit {@code draft_created} ; M15-09 {@code issued}, {@code
 * payment_recorded} (paiement à l'émission) et {@code draft_created} d'une duplication.
 */
@Entity
@Table(name = "invoice_event")
@Immutable
public class InvoiceEvent implements Persistable<UUID> {

  /** Brouillon créé (M15-08), ou dupliqué (M15-09). */
  public static final String DRAFT_CREATED = "draft_created";

  /** Facture émise (M15-09) : {@code detail = { number }}. */
  public static final String ISSUED = "issued";

  /** Paiement enregistré (M15-09 à l'émission, M15-10) : {@code detail = { paymentId, mode }}. */
  public static final String PAYMENT_RECORDED = "payment_recorded";

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID invoiceId;

  @Column(nullable = false, updatable = false)
  private String kind;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime at;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false)
  private Map<String, Object> detail;

  @Transient private boolean isNew = true;

  protected InvoiceEvent() {}

  public InvoiceEvent(
      UUID id, UUID invoiceId, String kind, OffsetDateTime at, Map<String, Object> detail) {
    this.id = Objects.requireNonNull(id);
    this.invoiceId = Objects.requireNonNull(invoiceId);
    this.kind = Objects.requireNonNull(kind);
    this.at = Objects.requireNonNull(at);
    this.detail = new LinkedHashMap<>(detail);
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

  public String getKind() {
    return kind;
  }

  public OffsetDateTime getAt() {
    return at;
  }

  public Map<String, Object> getDetail() {
    return detail == null ? Map.of() : new LinkedHashMap<>(detail);
  }
}
