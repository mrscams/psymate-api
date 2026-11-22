package com.psymate.agenda.model;

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
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Demande de rendez-vous en ligne d'un visiteur (table {@code booking_request}, V15, G Q73, Q82).
 * {@code tenant_id}, {@code practitioner_id} et {@code appointment_id} sont des UUID nus : aucune
 * association JPA (garde ArchUnit), même vers {@link Appointment}.
 *
 * <p>Tant que l'e-mail n'est pas vérifié, la ligne porte l'identité saisie (nom, prénom, e-mail,
 * téléphone). À la vérification, l'identité passe dans les colonnes {@code guest_*} du rendez-vous
 * créé et est effacée ici ({@code ck_booking_request_identity}) : il ne reste que des empreintes
 * ({@code email_hash}) et des jetons hachés. {@code verified_at} et {@code appointment_id} sont
 * posés ensemble ({@code ck_booking_request_verified}). Les transitions (vérification, clôture) et
 * les purges arrivent avec M14-06 et M14-07 ; M14-04 ne pose que la correspondance de la table.
 */
@Entity
@Table(name = "booking_request")
public class BookingRequest implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime startsAt;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime endsAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private AppointmentModality modality;

  // Identité saisie : NULL dès la vérification (G Q82 b).
  private String firstName;
  private String lastName;
  private String email;
  private String phone;

  /** Langue des e-mails au demandeur : {@code fr} ou {@code en} (G Q81). */
  @Column(nullable = false, updatable = false)
  private String locale;

  /** HMAC de l'adresse normalisée : plafonds par adresse (G Q79). */
  @Column(nullable = false, updatable = false)
  private String emailHash;

  @Column(nullable = false, updatable = false)
  private String verifyTokenHash;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime verifyExpiresAt;

  private OffsetDateTime verifiedAt;

  /** Empreinte du lien de gestion en cours (G Q80) ; remplacée à chaque e-mail de suivi. */
  private String manageTokenHash;

  /** Rendez-vous créé à la vérification ; null tant que la demande n'est pas vérifiée. */
  private UUID appointmentId;

  /** Qui a clos la demande : {@code visitor}, {@code practitioner} ou {@code expired}. */
  private String closedBy;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Transient private boolean isNew = true;

  protected BookingRequest() {}

  /**
   * Nouvelle demande, non vérifiée. {@code endsAt} doit suivre {@code startsAt} ({@code
   * ck_booking_request_order}) ; nom, prénom et e-mail sont obligatoires à ce stade (G Q72), le
   * téléphone est facultatif.
   */
  public BookingRequest(
      UUID id,
      UUID tenantId,
      UUID practitionerId,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      AppointmentModality modality,
      String firstName,
      String lastName,
      String email,
      String phone,
      String locale,
      String emailHash,
      String verifyTokenHash,
      OffsetDateTime verifyExpiresAt,
      OffsetDateTime createdAt) {
    this.id = Objects.requireNonNull(id);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.startsAt = Objects.requireNonNull(startsAt);
    this.endsAt = Objects.requireNonNull(endsAt);
    this.modality = Objects.requireNonNull(modality);
    this.firstName = Objects.requireNonNull(firstName);
    this.lastName = Objects.requireNonNull(lastName);
    this.email = Objects.requireNonNull(email);
    this.phone = phone;
    this.locale = Objects.requireNonNull(locale);
    this.emailHash = Objects.requireNonNull(emailHash);
    this.verifyTokenHash = Objects.requireNonNull(verifyTokenHash);
    this.verifyExpiresAt = Objects.requireNonNull(verifyExpiresAt);
    this.createdAt = Objects.requireNonNull(createdAt);
  }

  // ---- lecture

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public OffsetDateTime getStartsAt() {
    return startsAt;
  }

  public OffsetDateTime getEndsAt() {
    return endsAt;
  }

  public AppointmentModality getModality() {
    return modality;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getLocale() {
    return locale;
  }

  public String getEmailHash() {
    return emailHash;
  }

  public String getVerifyTokenHash() {
    return verifyTokenHash;
  }

  public OffsetDateTime getVerifyExpiresAt() {
    return verifyExpiresAt;
  }

  public OffsetDateTime getVerifiedAt() {
    return verifiedAt;
  }

  public String getManageTokenHash() {
    return manageTokenHash;
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public String getClosedBy() {
    return closedBy;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
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
    return this == o || (o instanceof BookingRequest other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
