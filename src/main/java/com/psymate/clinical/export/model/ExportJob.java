package com.psymate.clinical.export.model;

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
public class ExportJob implements Persistable<UUID> {

  public static final String QUEUED = "queued";
  public static final String RUNNING = "running";
  public static final String DONE = "done";
  public static final String FAILED = "failed";

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
  public ExportJob(UUID id, UUID practitionerId, OffsetDateTime requestedAt) {
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
  public boolean markRunning() {
    if (!QUEUED.equals(status)) {
      return false;
    }
    status = RUNNING;
    return true;
  }

  /** Fichier stocké : {@code done}, échéance posée, erreur d'une tentative précédente effacée. */
  public void markDone(String storageKey, OffsetDateTime finishedAt, OffsetDateTime expiresAt) {
    this.status = DONE;
    this.storageKey = Objects.requireNonNull(storageKey);
    this.finishedAt = Objects.requireNonNull(finishedAt);
    this.expiresAt = Objects.requireNonNull(expiresAt);
    this.error = null;
  }

  public void markFailed(String error, OffsetDateTime finishedAt) {
    this.status = FAILED;
    this.error = error;
    this.finishedAt = Objects.requireNonNull(finishedAt);
  }

  /**
   * Fichier supprimé : {@code purged_at} posé une seule fois (idempotent, la première date reste).
   *
   * @return vrai si la ligne vient d'être marquée, faux si elle l'était déjà
   */
  public boolean markPurged(OffsetDateTime at) {
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

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getRequestedAt() {
    return requestedAt;
  }

  public OffsetDateTime getFinishedAt() {
    return finishedAt;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getPurgedAt() {
    return purgedAt;
  }

  public String getError() {
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
