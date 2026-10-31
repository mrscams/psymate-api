package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminCursors.Position;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lecture seule de la vue admin_journal_v (E3.13, G Q29) jointe à practitioner pour les noms. La
 * vue ne porte ni adresse IP ni user_agent ; cette classe ne les lit jamais. Les lignes d'audit
 * patient (kind patient.*) sont écartées : le journal d'administration n'expose aucune donnée
 * patient, même un identifiant opaque.
 */
@Repository
class AdminJournalRepository {

  /** Critères du journal ; un critère null est ignoré. */
  record Filter(String kind, UUID practitionerId, UUID actorId, Instant from, Instant to) {}

  record JournalRow(
      UUID id,
      Instant at,
      String kind,
      String source,
      UUID actorId,
      String actorName,
      UUID practitionerId,
      String practitionerName,
      String summary,
      String detailsJson) {}

  private final JdbcClient jdbc;

  AdminJournalRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Page triée par date décroissante puis identifiant décroissant. Le type s'apparie en égalité ou
   * en préfixe (kind=admin donne admin et admin.*) ; le service garantit que kind ne contient ni %
   * ni _.
   */
  List<JournalRow> page(Filter f, Position cursor, int limit) {
    Map<String, Object> params = new HashMap<>();
    StringBuilder sql =
        new StringBuilder(
            "select j.id, j.occurred_at, j.kind, j.source, j.actor_id, a.display_name as actor_name,"
                + " j.practitioner_id, p.display_name as practitioner_name, j.summary,"
                + " j.details::text as details from admin_journal_v j"
                + " left join practitioner a on a.id = j.actor_id"
                + " left join practitioner p on p.id = j.practitioner_id"
                + " where j.kind not like 'patient.%'");
    if (f.kind() != null) {
      sql.append(" and (j.kind = :kind or j.kind like :kindPrefix)");
      params.put("kind", f.kind());
      params.put("kindPrefix", f.kind().replace("_", "\\_") + ".%");
    }
    if (f.practitionerId() != null) {
      sql.append(" and j.practitioner_id = :practitioner");
      params.put("practitioner", f.practitionerId());
    }
    if (f.actorId() != null) {
      sql.append(" and j.actor_id = :actor");
      params.put("actor", f.actorId());
    }
    if (f.from() != null) {
      sql.append(" and j.occurred_at >= :from");
      params.put("from", Timestamp.from(f.from()));
    }
    if (f.to() != null) {
      sql.append(" and j.occurred_at <= :to");
      params.put("to", Timestamp.from(f.to()));
    }
    if (cursor != null) {
      sql.append(
          " and (date_trunc('milliseconds', j.occurred_at), j.id) <"
              + " (timestamptz 'epoch' + cast(:key as bigint) * interval '1 millisecond', :cid)");
      params.put("key", cursor.key());
      params.put("cid", cursor.id());
    }
    sql.append(" order by date_trunc('milliseconds', j.occurred_at) desc, j.id desc limit :limit");
    params.put("limit", limit);
    return jdbc.sql(sql.toString())
        .params(params)
        .query(
            (rs, i) ->
                new JournalRow(
                    rs.getObject("id", UUID.class),
                    rs.getTimestamp("occurred_at").toInstant(),
                    rs.getString("kind"),
                    rs.getString("source"),
                    rs.getObject("actor_id", UUID.class),
                    rs.getString("actor_name"),
                    rs.getObject("practitioner_id", UUID.class),
                    rs.getString("practitioner_name"),
                    rs.getString("summary"),
                    rs.getString("details")))
        .list();
  }
}
