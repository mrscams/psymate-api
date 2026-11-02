package com.psymate.overview.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accès à {@code patient_overview} et lectures annexes de la projection. Ce module est une
 * projection de lecture (A5) : il lit en SQL direct {@code patient} (clinical_rev, dates, suivi),
 * {@code patient_flag} et {@code entry} (activité récente), car {@code PatientFacade} n'expose ni
 * la révision clinique ni les drapeaux.
 */
@Repository
public class OverviewRepository {

  /** Ligne de {@code patient_overview}. */
  public record Stored(String payload, long sourceRev, Instant computedAt, Integer computeMs) {}

  /**
   * État du patient utile au calcul et au contrôle de fraîcheur.
   *
   * @param changedAt dernière modification de la fiche ou des drapeaux (ne fait pas bouger {@code
   *     clinical_rev}), nulle si aucune
   */
  public record PatientState(
      long clinicalRev,
      LocalDate birthDate,
      String followUpSince,
      Instant createdAt,
      Instant changedAt) {}

  /**
   * Entrée récente du dossier, telle que lue ; le service en fait un {@code RecentActivityItemDto}.
   */
  public record RecentEntry(
      UUID entryId,
      String type,
      String title,
      Instant occurredFrom,
      Instant occurredTo,
      String status,
      String visibility,
      UUID sessionId,
      int version,
      Instant recordedAt) {}

  private final JdbcClient jdbc;

  OverviewRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public static OffsetDateTime odt(Instant i) {
    return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
  }

  public static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
    return t == null ? null : t.toInstant();
  }

  public Optional<Stored> find(UUID patientId) {
    return jdbc.sql(
            "select payload::text as payload, source_rev, computed_at, compute_ms"
                + " from patient_overview where patient_id = :p")
        .param("p", patientId)
        .query(
            (rs, i) ->
                new Stored(
                    rs.getString("payload"),
                    rs.getLong("source_rev"),
                    instant(rs, "computed_at"),
                    (Integer) rs.getObject("compute_ms")))
        .optional();
  }

  /**
   * Écrit la projection, sans jamais écraser une ligne calculée à une révision plus récente.
   * Idempotent : rejouer le même calcul laisse la même ligne.
   *
   * @return 1 si la ligne a été écrite, 0 si une ligne plus récente existe déjà
   */
  public int upsert(
      UUID patientId, String payload, long sourceRev, Instant computedAt, int computeMs) {
    return jdbc.sql(
            "insert into patient_overview (patient_id, payload, source_rev, computed_at, compute_ms)"
                + " values (:p, cast(:payload as jsonb), :rev, :at, :ms)"
                + " on conflict (patient_id) do update set payload = excluded.payload,"
                + " source_rev = excluded.source_rev, computed_at = excluded.computed_at,"
                + " compute_ms = excluded.compute_ms"
                + " where patient_overview.source_rev <= excluded.source_rev")
        .param("p", patientId)
        .param("payload", payload)
        .param("rev", sourceRev)
        .param("at", odt(computedAt))
        .param("ms", computeMs)
        .update();
  }

  public Optional<PatientState> patientState(UUID patientId) {
    return jdbc.sql(
            "select p.clinical_rev, p.birth_date, p.follow_up->>'since' as since, p.created_at,"
                + " greatest(p.updated_at, (select max(greatest(f.set_at,"
                + " coalesce(f.cleared_at, f.set_at))) from patient_flag f"
                + " where f.patient_id = p.id)) as changed_at"
                + " from patient p where p.id = :p")
        .param("p", patientId)
        .query(
            (rs, i) ->
                new PatientState(
                    rs.getLong("clinical_rev"),
                    rs.getObject("birth_date", LocalDate.class),
                    rs.getString("since"),
                    instant(rs, "created_at"),
                    instant(rs, "changed_at")))
        .optional();
  }

  /** Les {@code limit} entrées non archivées les plus récemment saisies ou corrigées. */
  public List<RecentEntry> recentActivity(UUID patientId, int limit) {
    return jdbc.sql(
            "select e.id, e.type, v.title, e.occurred_from, e.occurred_to, e.status, e.visibility,"
                + " e.session_id, e.current_version, e.recorded_at"
                + " from entry e left join entry_version v on v.id = e.current_version_id"
                + " where e.patient_id = :p and e.archived_at is null"
                + " order by e.updated_at desc, e.recorded_at desc, e.id desc limit :limit")
        .param("p", patientId)
        .param("limit", limit)
        .query(
            (rs, i) ->
                new RecentEntry(
                    rs.getObject("id", UUID.class),
                    rs.getString("type"),
                    rs.getString("title"),
                    instant(rs, "occurred_from"),
                    instant(rs, "occurred_to"),
                    rs.getString("status"),
                    rs.getString("visibility"),
                    rs.getObject("session_id", UUID.class),
                    rs.getInt("current_version"),
                    instant(rs, "recorded_at")))
        .list();
  }
}
