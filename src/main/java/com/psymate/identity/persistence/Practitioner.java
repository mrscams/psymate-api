package com.psymate.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Compte praticien (table {@code practitioner}). Interne au module identity : jamais exposée dans
 * un DTO, un événement ni l'API ({@code identity.api}). {@code tenant_id} et {@code verified_by}
 * sont des UUID nus.
 *
 * <p>{@code @DynamicUpdate} est obligatoire : la ligne est écrite par des flux indépendants
 * (connexion, compteur d'échecs, préférences, 2FA, vérification, décisions admin). Un UPDATE pleine
 * ligne écraserait les colonnes écrites entre-temps par un autre flux ; seules les colonnes
 * modifiées par la méthode métier appelée sont émises. Les compteurs atomiques (échecs de
 * connexion, dernière connexion) restent des UPDATE JPQL du repository.
 */
@Entity
@Table(name = "practitioner")
@DynamicUpdate
public class Practitioner implements Persistable<UUID> {

  /** Préférence posée sur le compte de bootstrap : le mot de passe est à changer (M7-08). */
  public static final String MUST_CHANGE_PASSWORD = "security.mustChangePassword";

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false)
  private String emailNormalized;

  private OffsetDateTime emailConfirmedAt;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String firstName;

  @Column(nullable = false)
  private String lastName;

  @Column(nullable = false)
  private String displayName;

  @Column(nullable = false)
  private String locale;

  @Column(nullable = false)
  private String profession;

  @Column(nullable = false, updatable = false)
  private String role;

  private String phone;
  private String professionalNumber;
  private String addressStreet;
  private String addressPostalCode;
  private String addressCity;

  @Column(nullable = false)
  private boolean remoteOnly;

  @Column(nullable = false)
  private String verificationStatus;

  private OffsetDateTime verifiedAt;
  private UUID verifiedBy;
  private OffsetDateTime suspendedAt;
  private OffsetDateTime purgeAfter;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, Object> preferences;

  private byte[] totpSecretEnc;
  private OffsetDateTime totpEnabledAt;

  @Column(nullable = false)
  private short failedLogins;

  private OffsetDateTime lockedUntil;
  private OffsetDateTime lastLoginAt;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Transient private boolean isNew = true;

  protected Practitioner() {}

  /** Inscription ouverte : rôle practitioner, e-mail à confirmer (email_pending). */
  public static Practitioner register(
      UUID id,
      UUID tenantId,
      String email,
      String emailNormalized,
      String passwordHash,
      String firstName,
      String lastName,
      String displayName,
      String locale,
      String profession,
      Map<String, Object> preferences,
      String phone,
      String professionalNumber,
      String addressStreet,
      String addressPostalCode,
      String addressCity,
      boolean remoteOnly,
      Instant now) {
    Practitioner p = new Practitioner();
    p.id = Objects.requireNonNull(id);
    p.tenantId = Objects.requireNonNull(tenantId);
    p.email = Objects.requireNonNull(email);
    p.emailNormalized = Objects.requireNonNull(emailNormalized);
    p.passwordHash = Objects.requireNonNull(passwordHash);
    p.firstName = Objects.requireNonNull(firstName);
    p.lastName = Objects.requireNonNull(lastName);
    p.displayName = Objects.requireNonNull(displayName);
    p.locale = Objects.requireNonNull(locale);
    p.profession = Objects.requireNonNull(profession);
    p.role = "practitioner";
    p.verificationStatus = "email_pending";
    p.preferences = copy(preferences);
    p.phone = phone;
    p.professionalNumber = professionalNumber;
    p.addressStreet = addressStreet;
    p.addressPostalCode = addressPostalCode;
    p.addressCity = addressCity;
    p.remoteOnly = remoteOnly;
    p.createdAt = Times.utc(now);
    p.updatedAt = p.createdAt;
    return p;
  }

  /**
   * Premier administrateur (M7-08) : rôle admin, approuvé, e-mail confirmé, préférence {@link
   * #MUST_CHANGE_PASSWORD}. La 2FA reste inactive (totp_* NULL, contrainte
   * ck_practitioner_admin_totp respectée). Valeurs techniques imposées par le schéma : profession
   * {@code psychologist}, prénom {@code Admin}, nom affiché {@code Administrateur}.
   */
  public static Practitioner bootstrapAdmin(
      UUID id,
      UUID tenantId,
      String emailNormalized,
      String passwordHash,
      String lastName,
      Instant now) {
    Practitioner p = new Practitioner();
    p.id = Objects.requireNonNull(id);
    p.tenantId = Objects.requireNonNull(tenantId);
    p.email = Objects.requireNonNull(emailNormalized);
    p.emailNormalized = emailNormalized;
    p.passwordHash = Objects.requireNonNull(passwordHash);
    p.firstName = "Admin";
    p.lastName = Objects.requireNonNull(lastName);
    p.displayName = "Administrateur";
    p.locale = "fr";
    p.profession = "psychologist";
    p.role = "admin";
    p.verificationStatus = "approved";
    p.remoteOnly = false;
    p.preferences = new LinkedHashMap<>(Map.of(MUST_CHANGE_PASSWORD, true));
    p.createdAt = Times.utc(now);
    p.emailConfirmedAt = p.createdAt;
    p.verifiedAt = p.createdAt;
    p.updatedAt = p.createdAt;
    return p;
  }

  private static Map<String, Object> copy(Map<String, Object> m) {
    return m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
  }

  // --- Méthodes métier : une par écriture, mêmes colonnes et mêmes conditions que l'ancien SQL ---

  /** Passe email_pending à docs_pending et pose email_confirmed_at (A10.3). */
  public void confirmEmail(Instant now) {
    this.emailConfirmedAt = Times.utc(now);
    this.updatedAt = this.emailConfirmedAt;
    if ("email_pending".equals(verificationStatus)) {
      this.verificationStatus = "docs_pending";
    }
  }

  public void changeEmail(String email, String emailNormalized, Instant now) {
    this.email = Objects.requireNonNull(email);
    this.emailNormalized = Objects.requireNonNull(emailNormalized);
    this.updatedAt = Times.utc(now);
  }

  public void changePassword(String passwordHash, Instant now) {
    this.passwordHash = Objects.requireNonNull(passwordHash);
    this.updatedAt = Times.utc(now);
  }

  /** Secret TOTP chiffré, en attente de vérification (activation remise à null). */
  public void setPendingTotpSecret(byte[] encrypted, Instant now) {
    this.totpSecretEnc = encrypted;
    this.totpEnabledAt = null;
    this.updatedAt = Times.utc(now);
  }

  /** Active la 2FA, seulement si un secret est en attente. */
  public void activateTotp(Instant now) {
    if (totpSecretEnc == null) {
      return;
    }
    this.totpEnabledAt = Times.utc(now);
    this.updatedAt = this.totpEnabledAt;
  }

  public void clearTotp(Instant now) {
    this.totpSecretEnc = null;
    this.totpEnabledAt = null;
    this.updatedAt = Times.utc(now);
  }

  /** Remplace toutes les préférences (n'écrit pas updated_at, comme l'ancien UPDATE). */
  public void replacePreferences(Map<String, Object> preferences) {
    this.preferences = copy(preferences);
  }

  public void setVerificationStatus(String status, Instant now) {
    this.verificationStatus = Objects.requireNonNull(status);
    this.updatedAt = Times.utc(now);
  }

  public void updateProfessionalInfo(
      String professionalNumber,
      String phone,
      String street,
      String postalCode,
      String city,
      boolean remoteOnly,
      Instant now) {
    this.professionalNumber = professionalNumber;
    this.phone = phone;
    this.addressStreet = street;
    this.addressPostalCode = postalCode;
    this.addressCity = city;
    this.remoteOnly = remoteOnly;
    this.updatedAt = Times.utc(now);
  }

  public void markApproved(UUID adminId, Instant now) {
    this.verificationStatus = "approved";
    this.verifiedAt = Times.utc(now);
    this.verifiedBy = adminId;
    this.updatedAt = this.verifiedAt;
  }

  public void markNeedsMore(Instant now) {
    this.verificationStatus = "needs_more";
    this.updatedAt = Times.utc(now);
  }

  public void markRejected(Instant now, Instant purgeAfter) {
    this.verificationStatus = "rejected";
    this.suspendedAt = Times.utc(now);
    this.purgeAfter = Times.utc(purgeAfter);
    this.updatedAt = this.suspendedAt;
  }

  public void markSuspended(Instant now) {
    this.verificationStatus = "suspended";
    this.suspendedAt = Times.utc(now);
    this.updatedAt = this.suspendedAt;
  }

  /** Réactivation : retour à {@code status}, suspension et date de purge effacées. */
  public void markReactivated(String status, Instant now) {
    this.verificationStatus = Objects.requireNonNull(status);
    this.suspendedAt = null;
    this.purgeAfter = null;
    this.updatedAt = Times.utc(now);
  }

  // --- Lecture ---

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getEmail() {
    return email;
  }

  public String getEmailNormalized() {
    return emailNormalized;
  }

  public OffsetDateTime getEmailConfirmedAt() {
    return emailConfirmedAt;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getLocale() {
    return locale;
  }

  public String getProfession() {
    return profession;
  }

  public String getRole() {
    return role;
  }

  public String getPhone() {
    return phone;
  }

  public String getProfessionalNumber() {
    return professionalNumber;
  }

  public String getAddressStreet() {
    return addressStreet;
  }

  public String getAddressPostalCode() {
    return addressPostalCode;
  }

  public String getAddressCity() {
    return addressCity;
  }

  public boolean isRemoteOnly() {
    return remoteOnly;
  }

  public String getVerificationStatus() {
    return verificationStatus;
  }

  public OffsetDateTime getVerifiedAt() {
    return verifiedAt;
  }

  public UUID getVerifiedBy() {
    return verifiedBy;
  }

  public OffsetDateTime getSuspendedAt() {
    return suspendedAt;
  }

  public OffsetDateTime getPurgeAfter() {
    return purgeAfter;
  }

  /** Vue de lecture : copiez-la avant de la sortir du module. */
  public Map<String, Object> getPreferences() {
    return preferences;
  }

  public byte[] getTotpSecretEnc() {
    return totpSecretEnc;
  }

  public OffsetDateTime getTotpEnabledAt() {
    return totpEnabledAt;
  }

  public short getFailedLogins() {
    return failedLogins;
  }

  public OffsetDateTime getLockedUntil() {
    return lockedUntil;
  }

  public OffsetDateTime getLastLoginAt() {
    return lastLoginAt;
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
    return this == o || (o instanceof Practitioner other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
