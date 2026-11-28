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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Réglages de facturation d'un praticien (table {@code billing_settings}, V18, L4, M15-06) : ce qui
 * figure sur chaque facture et ce que le module propose par défaut. Une ligne par praticien, créée
 * à la première écriture et modifiée sur place. Sans ligne, les réglages valent les valeurs par
 * défaut des colonnes ({@link #defaults}).
 *
 * <p>L'IBAN n'y est jamais (G Q95) : seuls ses quatre derniers caractères ({@code iban_last4}) ;
 * l'IBAN lui-même est rangé chiffré hors de la base. La mention de TVA n'est pas une colonne : elle
 * découle du régime (G Q92). {@code email_templates} ne garde que les modèles qui diffèrent du
 * texte par défaut ({@code {}} sinon). {@code tenant_id} est un UUID nu.
 */
@Entity
@Table(name = "billing_settings")
public class BillingSettings implements Persistable<UUID> {

  /** Régimes de TVA (G Q92). */
  public static final List<String> VAT_REGIMES = List.of("health_exempt", "franchise", "liable");

  /** Régime assujetti : taux et numéro de TVA obligatoires. */
  public static final String LIABLE = "liable";

  /** Échéances par défaut d'une facture. */
  public static final List<String> DUE_TERMS = List.of("on_receipt", "days_15", "days_30");

  /** Modes de paiement ({@code payment.mode}, V18). */
  public static final List<String> PAYMENT_MODES =
      List.of("transfer", "cheque", "cash", "card", "other");

  /** Modes acceptés par défaut (V18), dans l'ordre de {@link #PAYMENT_MODES}. */
  public static final List<String> DEFAULT_ACCEPTED_MODES = List.of("transfer", "cheque", "cash");

  @Id private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private UUID tenantId;

  private String siret;

  private String addressLines;

  private String displayEmail;

  private String displayPhone;

  @Column(nullable = false)
  private String vatRegime;

  private Integer vatRateBp;

  private String vatNumber;

  @Column(nullable = false)
  private String invoicePrefix;

  @Column(nullable = false)
  private String creditNotePrefix;

  @Column(nullable = false)
  private String dueTerm;

  private String chequePayee;

  /**
   * Tableau JSON de modes, lu dans l'ordre de {@link #PAYMENT_MODES}. {@code Set<String>} et non
   * {@code List<String>} : Hibernate partage le type résolu d'un même type Java entre entités, et
   * {@code List<String>} est déjà un tableau SQL ({@code text[]}) dans {@code LinkType}
   * (vocabulary) ; {@code String[]} est refusé en JSON.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Set<String> acceptedModes;

  @Column(nullable = false)
  private boolean showBankDetails;

  @Column(name = "iban_last4")
  private String ibanLast4;

  private String footerNote;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, Object> emailTemplates;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Transient private boolean isNew = true;

  protected BillingSettings() {}

  /**
   * Réglages par défaut d'un praticien sans ligne : valeurs par défaut des colonnes de V18. Rien
   * n'est écrit tant que {@code now} n'a pas servi à un enregistrement.
   */
  public static BillingSettings defaults(UUID practitionerId, UUID tenantId, OffsetDateTime now) {
    BillingSettings s = new BillingSettings();
    s.practitionerId = Objects.requireNonNull(practitionerId);
    s.tenantId = Objects.requireNonNull(tenantId);
    s.vatRegime = "health_exempt";
    s.invoicePrefix = BillingNumbering.DEFAULT_INVOICE_PREFIX;
    s.creditNotePrefix = BillingNumbering.DEFAULT_CREDIT_NOTE_PREFIX;
    s.dueTerm = "on_receipt";
    s.acceptedModes = new LinkedHashSet<>(DEFAULT_ACCEPTED_MODES);
    s.showBankDetails = false;
    s.emailTemplates = Map.of();
    s.createdAt = Objects.requireNonNull(now);
    s.updatedAt = now;
    return s;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  @Override
  public UUID getId() {
    return practitionerId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getSiret() {
    return siret;
  }

  public String getAddressLines() {
    return addressLines;
  }

  public String getDisplayEmail() {
    return displayEmail;
  }

  public String getDisplayPhone() {
    return displayPhone;
  }

  public String getVatRegime() {
    return vatRegime;
  }

  public Integer getVatRateBp() {
    return vatRateBp;
  }

  public String getVatNumber() {
    return vatNumber;
  }

  public String getInvoicePrefix() {
    return invoicePrefix;
  }

  public String getCreditNotePrefix() {
    return creditNotePrefix;
  }

  public String getDueTerm() {
    return dueTerm;
  }

  public String getChequePayee() {
    return chequePayee;
  }

  public List<String> getAcceptedModes() {
    if (acceptedModes == null) {
      return List.of();
    }
    return PAYMENT_MODES.stream().filter(acceptedModes::contains).toList();
  }

  public boolean isShowBankDetails() {
    return showBankDetails;
  }

  public String getIbanLast4() {
    return ibanLast4;
  }

  public String getFooterNote() {
    return footerNote;
  }

  /** Modèles d'e-mail enregistrés : {@code {invoice: {subject, body}, reminder: {…}}}, partiel. */
  public Map<String, Object> getEmailTemplates() {
    return emailTemplates == null ? Map.of() : new LinkedHashMap<>(emailTemplates);
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** Coordonnées affichées sur les factures (PUT). Chaque valeur est déjà validée et normalisée. */
  public void replaceContact(
      String siret,
      String addressLines,
      String displayEmail,
      String displayPhone,
      OffsetDateTime now) {
    this.siret = siret;
    this.addressLines = addressLines;
    this.displayEmail = displayEmail;
    this.displayPhone = displayPhone;
    this.updatedAt = Objects.requireNonNull(now);
  }

  /**
   * Régime de TVA (PUT). Hors régime assujetti, taux et numéro sont effacés : la contrainte de la
   * table exige un taux en régime assujetti.
   */
  public void replaceVat(String regime, Integer rateBp, String vatNumber, OffsetDateTime now) {
    this.vatRegime = Objects.requireNonNull(regime);
    boolean liable = LIABLE.equals(regime);
    this.vatRateBp = liable ? rateBp : null;
    this.vatNumber = liable ? vatNumber : null;
    this.updatedAt = Objects.requireNonNull(now);
  }

  /** Préfixes de numérotation (PUT) ; le verrou après émission est vérifié par l'appelant. */
  public void replacePrefixes(String invoicePrefix, String creditNotePrefix, OffsetDateTime now) {
    this.invoicePrefix = Objects.requireNonNull(invoicePrefix);
    this.creditNotePrefix = Objects.requireNonNull(creditNotePrefix);
    this.updatedAt = Objects.requireNonNull(now);
  }

  /** Modalités de règlement, mention de bas de page et modèles d'e-mail (PUT). */
  public void replaceTerms(
      String dueTerm,
      String chequePayee,
      List<String> acceptedModes,
      boolean showBankDetails,
      String footerNote,
      Map<String, Object> emailTemplates,
      OffsetDateTime now) {
    this.dueTerm = Objects.requireNonNull(dueTerm);
    this.chequePayee = chequePayee;
    this.acceptedModes =
        new LinkedHashSet<>(PAYMENT_MODES.stream().filter(acceptedModes::contains).toList());
    this.showBankDetails = showBankDetails;
    this.footerNote = footerNote;
    this.emailTemplates = new LinkedHashMap<>(emailTemplates);
    this.updatedAt = Objects.requireNonNull(now);
  }

  /**
   * Quatre derniers caractères de l'IBAN rangé chiffré, ou null quand l'IBAN est effacé. Jamais
   * l'IBAN entier.
   */
  public void changeIbanLast4(String last4, OffsetDateTime now) {
    if (last4 != null && last4.length() != 4) {
      throw new IllegalArgumentException("iban_last4 : quatre caractères");
    }
    this.ibanLast4 = last4;
    this.updatedAt = Objects.requireNonNull(now);
  }
}
