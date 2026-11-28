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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Facture ou avoir (table {@code invoice}, V18, L4 ; G Q88). M15-08 crée et modifie des brouillons
 * ({@code status = draft}, {@code kind = invoice}) : tout s'y modifie, et le brouillon se supprime
 * physiquement avec ses lignes et son historique (E9, {@code on delete cascade}). Émise ({@code
 * issued}) ou annulée ({@code void}), une facture ne change plus et ne se supprime jamais : {@link
 * #requireDraft} refuse toute écriture (409 INVOICE_ISSUED). L'émission ({@link #issue} : numéro,
 * instantané de l'émetteur {@code issuer_snapshot}, montants figés, PDF) vient de M15-09, qui tient
 * aussi {@code paid_cents} pour un paiement enregistré à l'émission ; paiements et avoirs de M15-10
 * et M15-11.
 *
 * <p>Montants en centimes entiers (G Q84). Pour un brouillon, {@code subtotal_cents}, {@code
 * vat_cents} et {@code total_cents} sont recalculés à chaque écriture avec les réglages du moment ;
 * la lecture recalcule la TVA avec les réglages courants (le bloc {@code issuer} d'un brouillon
 * reflète les réglages du moment, L5.3). Le destinataire ({@code recipient_name}, {@code
 * recipient_address}) est une donnée de la facture, posée à la création depuis l'identité de
 * facturation du patient et son profil, modifiable sur un brouillon. Aucun texte clinique, aucune
 * association JPA : identifiants en UUID nus. {@code @DynamicUpdate} : une écriture ne réécrit que
 * les colonnes changées.
 */
@Entity
@Table(name = "invoice")
@DynamicUpdate
public class Invoice implements Persistable<UUID> {

  public static final String KIND_INVOICE = "invoice";
  public static final String KIND_CREDIT_NOTE = "credit_note";

  public static final String DRAFT = "draft";
  public static final String ISSUED = "issued";
  public static final String VOID = "void";

  /** Échéance choisie : une date ({@code due_on}). */
  public static final String DUE_DATE = "date";

  /** Échéances d'une facture ({@code invoice.due_term}, V18). */
  public static final List<String> DUE_TERMS = List.of("on_receipt", "days_15", "days_30", "date");

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false)
  private UUID patientId;

  @Column(nullable = false, updatable = false)
  private String kind;

  @Column(nullable = false)
  private String status;

  private String number;

  @Column(updatable = false)
  private UUID cancelsInvoiceId;

  private String recipientName;

  private String recipientAddress;

  @Column(nullable = false)
  private LocalDate issueDate;

  @Column(nullable = false)
  private String dueTerm;

  private LocalDate dueOn;

  private String footerNote;

  private String reasonCode;

  private String reasonText;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> issuerSnapshot;

  @Column(nullable = false)
  private long subtotalCents;

  @Column(nullable = false)
  private long vatCents;

  @Column(nullable = false)
  private long totalCents;

  @Column(nullable = false)
  private long paidCents;

  @Column(nullable = false)
  private long creditedCents;

  @Column(nullable = false)
  private long refundDueCents;

  private String pdfStorageKey;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  private OffsetDateTime issuedAt;

  private OffsetDateTime voidedAt;

  @Transient private boolean isNew = true;

  protected Invoice() {}

  /**
   * Nouveau brouillon de facture : sans numéro, sans instantané, montants à zéro jusqu'au premier
   * calcul ({@link #changeTotals}).
   */
  public static Invoice draft(
      UUID id,
      UUID tenantId,
      UUID practitionerId,
      UUID patientId,
      String recipientName,
      String recipientAddress,
      LocalDate issueDate,
      String dueTerm,
      String footerNote,
      OffsetDateTime now) {
    Invoice i = new Invoice();
    i.id = Objects.requireNonNull(id);
    i.tenantId = Objects.requireNonNull(tenantId);
    i.practitionerId = Objects.requireNonNull(practitionerId);
    i.patientId = Objects.requireNonNull(patientId);
    i.kind = KIND_INVOICE;
    i.status = DRAFT;
    i.recipientName = recipientName;
    i.recipientAddress = recipientAddress;
    i.issueDate = Objects.requireNonNull(issueDate);
    i.dueTerm = Objects.requireNonNull(dueTerm);
    i.footerNote = footerNote;
    i.createdAt = Objects.requireNonNull(now);
    i.updatedAt = now;
    return i;
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

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public String getKind() {
    return kind;
  }

  public String getStatus() {
    return status;
  }

  public String getNumber() {
    return number;
  }

  public UUID getCancelsInvoiceId() {
    return cancelsInvoiceId;
  }

  public String getRecipientName() {
    return recipientName;
  }

  public String getRecipientAddress() {
    return recipientAddress;
  }

  public LocalDate getIssueDate() {
    return issueDate;
  }

  public String getDueTerm() {
    return dueTerm;
  }

  public LocalDate getDueOn() {
    return dueOn;
  }

  public String getFooterNote() {
    return footerNote;
  }

  /** Instantané de l'émetteur d'un document émis (M15-09) ; null pour un brouillon. */
  public Map<String, Object> getIssuerSnapshot() {
    return issuerSnapshot == null ? null : new LinkedHashMap<>(issuerSnapshot);
  }

  public long getSubtotalCents() {
    return subtotalCents;
  }

  public long getVatCents() {
    return vatCents;
  }

  public long getTotalCents() {
    return totalCents;
  }

  public long getPaidCents() {
    return paidCents;
  }

  public long getCreditedCents() {
    return creditedCents;
  }

  public long getRefundDueCents() {
    return refundDueCents;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** Clé du PDF rangé chiffré à l'émission (M15-09) ; null pour un brouillon. */
  public String getPdfStorageKey() {
    return pdfStorageKey;
  }

  public OffsetDateTime getIssuedAt() {
    return issuedAt;
  }

  public boolean isDraft() {
    return DRAFT.equals(status);
  }

  /** Seul un brouillon se modifie ou se supprime (G Q88) : sinon 409 INVOICE_ISSUED. */
  public void requireDraft() {
    if (!isDraft()) {
      throw BillingException.invoiceIssued();
    }
  }

  /** Patient du brouillon (tant qu'aucune ligne de séance, vérifié par l'appelant). */
  public void changePatient(UUID patientId, OffsetDateTime now) {
    requireDraft();
    this.patientId = Objects.requireNonNull(patientId);
    this.updatedAt = Objects.requireNonNull(now);
  }

  /** Destinataire du brouillon : nom et adresse, déjà validés. */
  public void changeRecipient(String name, String address, OffsetDateTime now) {
    requireDraft();
    this.recipientName = name;
    this.recipientAddress = address;
    this.updatedAt = Objects.requireNonNull(now);
  }

  /** Date d'émission prévue, échéance et date d'échéance (null hors échéance {@code date}). */
  public void changeDates(
      LocalDate issueDate, String dueTerm, LocalDate dueOn, OffsetDateTime now) {
    requireDraft();
    this.issueDate = Objects.requireNonNull(issueDate);
    this.dueTerm = Objects.requireNonNull(dueTerm);
    this.dueOn = DUE_DATE.equals(dueTerm) ? Objects.requireNonNull(dueOn) : null;
    this.updatedAt = Objects.requireNonNull(now);
  }

  /** Mention de bas de page du brouillon ; null l'efface. */
  public void changeFooterNote(String footerNote, OffsetDateTime now) {
    requireDraft();
    this.footerNote = footerNote;
    this.updatedAt = Objects.requireNonNull(now);
  }

  /**
   * Émission (G Q88, Q89, M15-09) : numéro attribué, instantané de l'émetteur, échéance effective
   * (L3.2), montants figés, clé du PDF rangé. Le destinataire ({@code recipient_name}, {@code
   * recipient_address}) et la date d'émission sont ceux du brouillon, figés avec lui. Seul un
   * brouillon s'émet (409 INVOICE_ISSUED sinon) ; ensuite plus rien ne change hors des compteurs de
   * paiement et d'avoir.
   */
  public void issue(
      String number,
      Map<String, Object> issuerSnapshot,
      LocalDate dueOn,
      InvoiceTotals totals,
      String pdfStorageKey,
      OffsetDateTime now) {
    requireDraft();
    this.status = ISSUED;
    this.number = Objects.requireNonNull(number);
    this.issuerSnapshot = new LinkedHashMap<>(Objects.requireNonNull(issuerSnapshot));
    this.dueOn = Objects.requireNonNull(dueOn);
    this.subtotalCents = totals.subtotal();
    this.vatCents = totals.vat();
    this.totalCents = totals.total();
    this.pdfStorageKey = Objects.requireNonNull(pdfStorageKey);
    this.issuedAt = Objects.requireNonNull(now);
    this.updatedAt = now;
  }

  /**
   * Paiement enregistré sur une facture émise (G Q90) : {@code paid_cents} tenu dans la même
   * transaction que la ligne {@code payment}.
   */
  public void recordPayment(long amountCents, OffsetDateTime now) {
    if (!ISSUED.equals(status) || !KIND_INVOICE.equals(kind)) {
      throw new IllegalStateException("paiement sur une facture qui n'est pas émise");
    }
    this.paidCents = Math.addExact(this.paidCents, amountCents);
    this.updatedAt = Objects.requireNonNull(now);
  }

  /** Montants du brouillon, recalculés à chaque écriture (L3.2). */
  public void changeTotals(InvoiceTotals totals, OffsetDateTime now) {
    requireDraft();
    this.subtotalCents = totals.subtotal();
    this.vatCents = totals.vat();
    this.totalCents = totals.total();
    this.updatedAt = Objects.requireNonNull(now);
  }
}
