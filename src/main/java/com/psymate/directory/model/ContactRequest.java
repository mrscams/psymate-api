package com.psymate.directory.model;

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
 * Demande de contact reçue par le formulaire public (table {@code contact_request}, E3.10) : trace
 * technique de 30 jours pour l'anti-spam, jamais liée à un dossier patient. La purge est un DELETE
 * physique voulu (E2). {@code practitioner_id} est un UUID nu (module identity).
 */
@Entity
@Table(name = "contact_request")
public class ContactRequest implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private String senderName;

  @Column(nullable = false, updatable = false)
  private String senderEmail;

  @Column(updatable = false)
  private String subject;

  @Column(nullable = false, updatable = false)
  private String message;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime consentAt;

  @Column(nullable = false, updatable = false)
  private String ipHash;

  private OffsetDateTime relayedAt;
  private String relayError;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime purgeAfter;

  @Transient private boolean isNew = true;

  protected ContactRequest() {}

  public ContactRequest(
      UUID id,
      UUID practitionerId,
      String senderName,
      String senderEmail,
      String subject,
      String message,
      OffsetDateTime consentAt,
      String ipHash,
      OffsetDateTime createdAt,
      OffsetDateTime purgeAfter) {
    this.id = Objects.requireNonNull(id);
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.senderName = Objects.requireNonNull(senderName);
    this.senderEmail = Objects.requireNonNull(senderEmail);
    this.subject = subject;
    this.message = Objects.requireNonNull(message);
    this.consentAt = Objects.requireNonNull(consentAt);
    this.ipHash = Objects.requireNonNull(ipHash);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.purgeAfter = Objects.requireNonNull(purgeAfter);
  }

  /** Le relais e-mail a abouti : l'éventuelle erreur d'une tentative précédente est effacée. */
  public void markRelayed(OffsetDateTime at) {
    this.relayedAt = Objects.requireNonNull(at);
    this.relayError = null;
  }

  public void markRelayFailed(String error) {
    this.relayError = error;
  }

  @Override
  public UUID getId() {
    return id;
  }

  UUID getPractitionerId() {
    return practitionerId;
  }

  String getSenderName() {
    return senderName;
  }

  String getSenderEmail() {
    return senderEmail;
  }

  String getSubject() {
    return subject;
  }

  String getMessage() {
    return message;
  }

  OffsetDateTime getConsentAt() {
    return consentAt;
  }

  String getIpHash() {
    return ipHash;
  }

  OffsetDateTime getRelayedAt() {
    return relayedAt;
  }

  String getRelayError() {
    return relayError;
  }

  OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  OffsetDateTime getPurgeAfter() {
    return purgeAfter;
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
    return this == o || (o instanceof ContactRequest other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
