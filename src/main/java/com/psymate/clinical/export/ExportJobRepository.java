package com.psymate.clinical.export;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Couture entre les services d'export et l'entité {@link ExportJob} (table export_job, E3.6, V11) :
 * renvoie des records, jamais d'entité. La demande ({@link #lockPractitioner}, {@link #failStale},
 * {@link #hasActive}, {@link #insert}) exige la transaction du service, qui tient le verrou
 * advisory jusqu'au commit ; les transitions du travail asynchrone et de la purge ({@link
 * #markRunning}, {@link #markDone}, {@link #markFailed}, {@link #markPurged}) ouvrent chacune leur
 * propre transaction courte, sans entité réutilisée d'un appel à l'autre.
 */
@Repository
class ExportJobRepository {

  /** Ligne export_job, vue de lecture pour les services. */
  record ExportJobView(
      UUID id,
      UUID practitionerId,
      String status,
      Instant requestedAt,
      Instant finishedAt,
      String storageKey,
      Instant expiresAt,
      Instant purgedAt,
      String error) {}

  private static final List<String> ACTIVE = List.of(ExportJob.QUEUED, ExportJob.RUNNING);

  private final ExportJobJpaRepository jobs;
  private final JdbcClient jdbc;

  ExportJobRepository(ExportJobJpaRepository jobs, JdbcClient jdbc) {
    this.jobs = jobs;
    this.jdbc = jdbc;
  }

  /** Instant → UTC à la précision de PostgreSQL (microsecondes). */
  static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  private static Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  private static ExportJobView view(ExportJob j) {
    return new ExportJobView(
        j.getId(),
        j.getPractitionerId(),
        j.getStatus(),
        instant(j.getRequestedAt()),
        instant(j.getFinishedAt()),
        j.getStorageKey(),
        instant(j.getExpiresAt()),
        instant(j.getPurgedAt()),
        j.getError());
  }

  @Transactional(readOnly = true)
  Optional<ExportJobView> find(UUID id) {
    return jobs.findById(id).map(ExportJobRepository::view);
  }

  // ---- demande : dans la transaction du service

  /**
   * Verrou de transaction : sérialise les demandes d'un même praticien (un seul job en cours).
   * Natif ({@code pg_advisory_xact_lock}, tenu jusqu'au commit), sur la connexion de la transaction
   * JPA courante.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  void lockPractitioner(UUID practitionerId) {
    jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:k, 0))")
        .param("k", "export:" + practitionerId)
        .query()
        .listOfRows();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  boolean hasActive(UUID practitionerId) {
    return jobs.existsByPractitionerIdAndStatusIn(practitionerId, ACTIVE);
  }

  /** Nouvelle ligne {@code queued} ; l'INSERT part au commit de la transaction du service. */
  @Transactional(propagation = Propagation.MANDATORY)
  void insert(UUID id, UUID practitionerId, Instant now) {
    jobs.save(new ExportJob(id, practitionerId, utc(now)));
  }

  /**
   * Marque en échec les jobs queued ou running du praticien plus anciens que {@code before} : ils
   * ont été interrompus (arrêt du serveur) et ne doivent pas bloquer les demandes suivantes.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  void failStale(UUID practitionerId, Instant before, Instant now) {
    jobs.failStale(practitionerId, utc(before), utc(now));
  }

  // ---- travail asynchrone et purge : une transaction courte par transition

  /**
   * Passe queued → running sous verrou de ligne : deux exécutions concurrentes se sérialisent et la
   * seconde relit le statut posé par la première.
   *
   * @return faux si le job n'existe pas ou n'est plus en attente (déjà pris ou terminé)
   */
  @Transactional
  boolean markRunning(UUID id) {
    return jobs.findForUpdate(id).map(ExportJob::markRunning).orElse(false);
  }

  @Transactional
  void markDone(UUID id, String storageKey, Instant finishedAt, Instant expiresAt) {
    jobs.findById(id).ifPresent(j -> j.markDone(storageKey, utc(finishedAt), utc(expiresAt)));
  }

  @Transactional
  void markFailed(UUID id, String error, Instant finishedAt) {
    jobs.findById(id).ifPresent(j -> j.markFailed(error, utc(finishedAt)));
  }

  /** Jobs terminés dont l'échéance est passée et dont le fichier n'est pas encore supprimé. */
  @Transactional(readOnly = true)
  List<ExportJobView> dueForPurge(Instant now) {
    return jobs.dueForPurge(utc(now)).stream().map(ExportJobRepository::view).toList();
  }

  /** Idempotent : la première date de purge reste. */
  @Transactional
  void markPurged(UUID id, Instant now) {
    jobs.findForUpdate(id).ifPresent(j -> j.markPurged(utc(now)));
  }
}
