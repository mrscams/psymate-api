package com.psymate.clinical.export;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Accès SQL à export_job (E3.6, V11) : une ligne par export du compte, jamais supprimée. */
@Repository
class ExportJobRepository {

  /** Ligne export_job. */
  record ExportJob(
      UUID id,
      UUID practitionerId,
      String status,
      Instant requestedAt,
      Instant finishedAt,
      String storageKey,
      Instant expiresAt,
      Instant purgedAt,
      String error) {}

  private static final String COLS =
      "id, practitioner_id, status, requested_at, finished_at, storage_key, expires_at,"
          + " purged_at, error";

  private final JdbcClient jdbc;

  ExportJobRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static OffsetDateTime odt(Instant i) {
    return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet rs, String col) throws SQLException {
    OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
    return o == null ? null : o.toInstant();
  }

  private static ExportJob job(ResultSet rs, int i) throws SQLException {
    return new ExportJob(
        rs.getObject("id", UUID.class),
        rs.getObject("practitioner_id", UUID.class),
        rs.getString("status"),
        instant(rs, "requested_at"),
        instant(rs, "finished_at"),
        rs.getString("storage_key"),
        instant(rs, "expires_at"),
        instant(rs, "purged_at"),
        rs.getString("error"));
  }

  Optional<ExportJob> find(UUID id) {
    return jdbc.sql("select " + COLS + " from export_job where id = :id")
        .param("id", id)
        .query(ExportJobRepository::job)
        .optional();
  }

  /** Verrou de transaction : sérialise les demandes d'un même praticien (un seul job en cours). */
  void lockPractitioner(UUID practitionerId) {
    jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:k, 0))")
        .param("k", "export:" + practitionerId)
        .query()
        .listOfRows();
  }

  boolean hasActive(UUID practitionerId) {
    return jdbc.sql(
                "select count(*) from export_job where practitioner_id = :p"
                    + " and status in ('queued','running')")
            .param("p", practitionerId)
            .query(Integer.class)
            .single()
        > 0;
  }

  void insert(UUID id, UUID practitionerId, Instant now) {
    jdbc.sql(
            "insert into export_job (id, practitioner_id, status, requested_at)"
                + " values (:id, :p, 'queued', :now)")
        .param("id", id)
        .param("p", practitionerId)
        .param("now", odt(now))
        .update();
  }

  /**
   * Marque en échec les jobs queued ou running du praticien plus anciens que {@code before} : ils
   * ont été interrompus (arrêt du serveur) et ne doivent pas bloquer les demandes suivantes.
   */
  void failStale(UUID practitionerId, Instant before, Instant now) {
    jdbc.sql(
            "update export_job set status = 'failed', error = 'interrupted', finished_at = :now"
                + " where practitioner_id = :p and status in ('queued','running')"
                + " and requested_at < :before")
        .param("p", practitionerId)
        .param("before", odt(before))
        .param("now", odt(now))
        .update();
  }

  /** Passe queued -> running ; faux si le job n'est plus en attente (déjà pris ou terminé). */
  boolean markRunning(UUID id) {
    return jdbc.sql("update export_job set status = 'running' where id = :id and status = 'queued'")
            .param("id", id)
            .update()
        == 1;
  }

  void markDone(UUID id, String storageKey, Instant finishedAt, Instant expiresAt) {
    jdbc.sql(
            "update export_job set status = 'done', storage_key = :key, finished_at = :fin,"
                + " expires_at = :exp, error = null where id = :id")
        .param("id", id)
        .param("key", storageKey)
        .param("fin", odt(finishedAt))
        .param("exp", odt(expiresAt))
        .update();
  }

  void markFailed(UUID id, String error, Instant finishedAt) {
    jdbc.sql(
            "update export_job set status = 'failed', error = :error, finished_at = :fin"
                + " where id = :id")
        .param("id", id)
        .param("error", error)
        .param("fin", odt(finishedAt))
        .update();
  }

  /** Jobs terminés dont l'échéance est passée et dont le fichier n'est pas encore supprimé. */
  List<ExportJob> dueForPurge(Instant now) {
    return jdbc.sql(
            "select "
                + COLS
                + " from export_job where status = 'done' and purged_at is null"
                + " and expires_at < :now order by expires_at")
        .param("now", odt(now))
        .query(ExportJobRepository::job)
        .list();
  }

  void markPurged(UUID id, Instant now) {
    jdbc.sql("update export_job set purged_at = :now where id = :id and purged_at is null")
        .param("id", id)
        .param("now", odt(now))
        .update();
  }
}
