package com.psymate.clinical.export;

import com.psymate.clinical.domain.TimeSpan;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lecture SQL du dossier à exporter. Les filtres de confidentialité sont ici et nulle part ailleurs
 * : {@code visibility = 'clinical'} (A4.7, une entrée privée n'est jamais lue, ni son titre, ni son
 * corps, ni ses versions) et {@code archived_at is null}. Le texte vient de la version courante
 * seulement ({@code entry.current_version_id}) : aucune ancienne version n'est jointe.
 */
@Repository
class ExportRepository {

  /** Séance exportable : détail entry_session et version courante (compte rendu). */
  record SessionRow(
      UUID id,
      TimeSpan time,
      String kind,
      String modality,
      Integer durationMin,
      String title,
      String body) {}

  /** Entrée exportable hors séance, avec ses détails typés. */
  record EntryRow(
      UUID id,
      UUID sessionId,
      String type,
      boolean draft,
      TimeSpan time,
      String title,
      String body,
      String emotionCode,
      Integer intensity,
      String molecule,
      String dosage,
      String frequency,
      LocalDate periodFrom,
      LocalDate periodTo) {}

  private static final String ORDER = " order by e.occurred_from, e.created_at, e.id";

  private final JdbcClient jdbc;

  ExportRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static Instant instant(ResultSet rs, String col) throws SQLException {
    OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
    return o == null ? null : o.toInstant();
  }

  private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
    int v = rs.getInt(col);
    return rs.wasNull() ? null : v;
  }

  private static TimeSpan time(ResultSet rs) throws SQLException {
    return new TimeSpan(
        instant(rs, "occurred_from"),
        instant(rs, "occurred_to"),
        TimeSpan.Precision.fromCode(rs.getString("occurred_precision")),
        rs.getString("occurred_label"));
  }

  /** Séances non archivées et cliniques, de la plus ancienne à la plus récente. */
  List<SessionRow> sessions(UUID patientId) {
    return jdbc.sql(
            "select e.id, e.occurred_from, e.occurred_to, e.occurred_precision, e.occurred_label,"
                + " s.kind, s.modality, s.duration_min, v.title, v.body"
                + " from entry e join entry_session s on s.entry_id = e.id"
                + " left join entry_version v on v.id = e.current_version_id"
                + " where e.patient_id = :p and e.type = 'session' and e.archived_at is null"
                + " and e.visibility = 'clinical'"
                + ORDER)
        .param("p", patientId)
        .query(
            (rs, i) ->
                new SessionRow(
                    rs.getObject("id", UUID.class),
                    time(rs),
                    rs.getString("kind"),
                    rs.getString("modality"),
                    nullableInt(rs, "duration_min"),
                    rs.getString("title"),
                    rs.getString("body")))
        .list();
  }

  /** Entrées (hors séances) non archivées et cliniques, de la plus ancienne à la plus récente. */
  List<EntryRow> entries(UUID patientId) {
    return jdbc.sql(
            "select e.id, e.session_id, e.type, e.status, e.occurred_from, e.occurred_to,"
                + " e.occurred_precision, e.occurred_label, v.title, v.body, f.emotion_code,"
                + " f.intensity, m.molecule, m.dosage, m.frequency, m.period_from, m.period_to"
                + " from entry e left join entry_version v on v.id = e.current_version_id"
                + " left join entry_feeling f on f.entry_id = e.id"
                + " left join entry_medication m on m.entry_id = e.id"
                + " where e.patient_id = :p and e.type <> 'session' and e.archived_at is null"
                + " and e.visibility = 'clinical'"
                + ORDER)
        .param("p", patientId)
        .query(
            (rs, i) ->
                new EntryRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("session_id", UUID.class),
                    rs.getString("type"),
                    "draft".equals(rs.getString("status")),
                    time(rs),
                    rs.getString("title"),
                    rs.getString("body"),
                    rs.getString("emotion_code"),
                    nullableInt(rs, "intensity"),
                    rs.getString("molecule"),
                    rs.getString("dosage"),
                    rs.getString("frequency"),
                    rs.getObject("period_from", LocalDate.class),
                    rs.getObject("period_to", LocalDate.class)))
        .list();
  }

  /** Tags posés sur les entrées exportables du patient : entrée -> identifiants de tags. */
  Map<UUID, List<UUID>> tagIds(UUID patientId) {
    Map<UUID, List<UUID>> byEntry = new LinkedHashMap<>();
    jdbc.sql(
            "select t.entry_id, t.tag_id from entry_tag t join entry e on e.id = t.entry_id"
                + " where e.patient_id = :p and e.archived_at is null"
                + " and e.visibility = 'clinical'")
        .param("p", patientId)
        .query(
            (rs, i) -> {
              byEntry
                  .computeIfAbsent(rs.getObject("entry_id", UUID.class), k -> new ArrayList<>())
                  .add(rs.getObject("tag_id", UUID.class));
              return 0;
            })
        .list();
    return byEntry;
  }
}
