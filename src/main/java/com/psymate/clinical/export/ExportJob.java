package com.psymate.clinical.export;

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
 * Export du compte d'un praticien (table {@code export_job}, E3.6, V11) : une ligne par demande,
 * jamais supprimée ; {@code purged_at} marque la suppression du fichier, la ligne reste. Cycle :
 * {@code queued} → {@code running} → {@code done} ou {@code failed}. {@code practitioner_id} est un
 * UUID nu (module identity, aucune association JPA).
 */
@Entity
@Table(name = "export_job")
class ExportJob implements Persistable<UUID> {

  static final String QUEUED = "queued";
  static final String RUNNING = "running";
  static final String DONE = "done";
  static final String FAILED = "failed";

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime requestedAt;

  private OffsetDateTime finishedAt;
  private String storageKey;
  private OffsetDateTime expiresAt;
  private OffsetDateTime purgedAt;
  private String error;

  @Transient private boolean isNew = true;

  protected ExportJob() {}

  /** Nouvelle demande : {@code queued}, sans fichier ni échéance. */
  ExportJob(UUID id, UUID practitionerId, OffsetDateTime requestedAt) {
    this.id = Objects.requireNonNull(id);
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.requestedAt = Objects.requireNonNull(requestedAt);
    this.status = QUEUED;
  }

  /**
   * Passe {@code queued} → {@code running}.
   *
   * @return faux si le job n'était plus en attente (déjà pris ou terminé) : rien n'est modifié
   */
  boolean markRunning() {
    if (!QUEUED.equals(status)) {
      return false;
    }
    status = RUNNING;
    return true;
  }

  /** Fichier stocké : {@code done}, échéance posée, erreur d'une tentative précédente effacée. */
  void markDone(String storageKey, OffsetDateTime finishedAt, OffsetDateTime expiresAt) {
    this.status = DONE;
    this.storageKey = Objects.requireNonNull(storageKey);
    this.finishedAt = Objects.requireNonNull(finishedAt);
    this.expiresAt = Objects.requireNonNull(expiresAt);
    this.error = null;
  }

  void markFailed(String error, OffsetDateTime finishedAt) {
    this.status = FAILED;
    this.error = error;
    this.finishedAt = Objects.requireNonNull(finishedAt);
  }

  /**
   * Fichier supprimé : {@code purged_at} posé une seule fois (idempotent, la première date reste).
   *
   * @return vrai si la ligne vient d'être marquée, faux si elle l'était déjà
   */
  boolean markPurged(OffsetDateTime at) {
    if (purgedAt != null) {
      return false;
    }
    purgedAt = Objects.requireNonNull(at);
    return true;
  }

  @Override
  public UUID getId() {
    return id;
  }

  UUID getPractitionerId() {
    return practitionerId;
  }

  String getStatus() {
    return status;
  }

  OffsetDateTime getRequestedAt() {
    return requestedAt;
  }

  OffsetDateTime getFinishedAt() {
    return finishedAt;
  }

  String getStorageKey() {
    return storageKey;
  }

  OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  OffsetDateTime getPurgedAt() {
    return purgedAt;
  }

  String getError() {
    return error;
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
    return this == o || (o instanceof ExportJob other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
