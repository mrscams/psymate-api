package com.psymate.clinical.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lectures de la timeline (A6) : fenêtre keyset sur (occurred_from, id), facettes, situations qui
 * chevauchent, résumés par période. SQL paramétré, filtres ajoutés seulement s'ils sont demandés.
 */
@Repository
public class TimelineRepository {

  /**
   * Filtres communs. Archivées toujours exclues. Listes nulles ou vides = pas de filtre, sauf
   * {@code tagIds} : nul = pas de filtre, vide = rien ne correspond.
   */
  public record Filter(
      UUID patientId,
      Instant from,
      Instant to,
      List<String> types,
      List<String> emotions,
      List<UUID> tagIds,
      boolean draftsOnly,
      boolean includePrivate) {}

  /** Ligne de timeline : colonnes communes, titre de la version courante, ressenti éventuel. */
  public record Row(
      UUID id,
      String type,
      String status,
      String visibility,
      String authorType,
      Instant occurredFrom,
      Instant occurredTo,
      String precision,
      String label,
      UUID sessionId,
      int linkCount,
      Instant recordedAt,
      String title,
      String emotionCode,
      Integer intensity) {}

  /** Entrées d'une période et d'un type. */
  public record PeriodTypeCount(LocalDateTime period, String type, int count) {}

  /** Ressentis d'une période : nombre et intensité moyenne. */
  public record PeriodFeelings(LocalDateTime period, int count, double averageIntensity) {}

  private static final String ROW_SELECT =
      "select e.id, e.type, e.status, e.visibility, e.author_type, e.occurred_from,"
          + " e.occurred_to, e.occurred_precision, e.occurred_label, e.session_id, e.link_count,"
          + " e.recorded_at, v.title, f.emotion_code, f.intensity"
          + " from entry e join entry_version v on v.id = e.current_version_id"
          + " left join entry_feeling f on f.entry_id = e.id where ";

  private static final String ORDER = " order by e.occurred_from desc, e.id desc";

  private final JdbcClient jdbc;

  TimelineRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Clause where et paramètres nommés construits ensemble. */
  private static final class Where {
    private final StringBuilder sql = new StringBuilder();
    private final Map<String, Object> params = new HashMap<>();

    Where and(String clause) {
      if (!sql.isEmpty()) {
        sql.append(" and ");
      }
      sql.append(clause);
      return this;
    }

    Where and(String clause, String name, Object value) {
      params.put(name, value);
      return and(clause);
    }

    String sql() {
      return sql.toString();
    }
  }

  /**
   * Prédicats communs : patient, archivées, visibilité, statut. {@code window} borne occurred_from
   * par from et to ; {@code content} ajoute types, émotions et tags.
   */
  private static Where where(Filter f, boolean window, boolean content) {
    Where w =
        new Where()
            .and("e.patient_id = :patient and e.archived_at is null", "patient", f.patientId());
    if (!f.includePrivate()) {
      w.and("e.visibility <> 'private'");
    }
    if (f.draftsOnly()) {
      w.and("e.status = 'draft'");
    }
    if (window && f.from() != null) {
      w.and("e.occurred_from >= :from", "from", EntryRepository.odt(f.from()));
    }
    if (window && f.to() != null) {
      w.and("e.occurred_from < :to", "to", EntryRepository.odt(f.to()));
    }
    if (content) {
      if (f.types() != null && !f.types().isEmpty()) {
        w.and("e.type = any(cast(:types as text[]))", "types", f.types().toArray(new String[0]));
      }
      if (f.emotions() != null && !f.emotions().isEmpty()) {
        w.and(
            "exists (select 1 from entry_feeling ef where ef.entry_id = e.id"
                + " and ef.emotion_code = any(cast(:emotions as text[])))",
            "emotions",
            f.emotions().toArray(new String[0]));
      }
      if (f.tagIds() != null) {
        if (f.tagIds().isEmpty()) {
          w.and("false");
        } else {
          w.and(
              "exists (select 1 from entry_tag et where et.entry_id = e.id"
                  + " and et.tag_id = any(:tagIds))",
              "tagIds",
              f.tagIds().toArray(new UUID[0]));
        }
      }
    }
    return w;
  }

  private static Instant instant(ResultSet rs, String col) throws SQLException {
    OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
    return o == null ? null : o.toInstant();
  }

  private static Row row(ResultSet rs, int i) throws SQLException {
    int intensity = rs.getInt("intensity");
    Integer nullableIntensity = rs.wasNull() ? null : intensity;
    return new Row(
        rs.getObject("id", UUID.class),
        rs.getString("type"),
        rs.getString("status"),
        rs.getString("visibility"),
        rs.getString("author_type"),
        instant(rs, "occurred_from"),
        instant(rs, "occurred_to"),
        rs.getString("occurred_precision"),
        rs.getString("occurred_label"),
        rs.getObject("session_id", UUID.class),
        rs.getInt("link_count"),
        instant(rs, "recorded_at"),
        rs.getString("title"),
        rs.getString("emotion_code"),
        nullableIntensity);
  }

  // ---- fenêtre

  /**
   * Au plus {@code fetch} lignes, de la plus récente à la plus ancienne, strictement après le
   * curseur (occurred_from, id) s'il est donné.
   */
  public List<Row> page(Filter filter, Instant cursorFrom, UUID cursorId, int fetch) {
    Where w = where(filter, true, true);
    if (cursorFrom != null && cursorId != null) {
      w.and(
          "(e.occurred_from, e.id) < (cast(:cursorFrom as timestamptz), cast(:cursorId as uuid))",
          "cursorFrom",
          EntryRepository.odt(cursorFrom));
      w.params.put("cursorId", cursorId);
    }
    w.params.put("fetch", fetch);
    return jdbc.sql(ROW_SELECT + w.sql() + ORDER + " limit :fetch")
        .params(w.params)
        .query(TimelineRepository::row)
        .list();
  }

  /**
   * Situations non archivées dont la plage chevauche la fenêtre (une situation commencée avant
   * {@code from} et sans fin y est). Ni types, ni émotions, ni tags : seuls les filtres de statut,
   * de visibilité et de fenêtre s'appliquent.
   */
  public List<Row> overlappingSituations(Filter filter) {
    Where w = where(filter, false, false).and("e.type = 'situation'");
    String lower = "'-infinity'::timestamptz";
    String upper = "'infinity'::timestamptz";
    if (filter.from() != null) {
      lower = "cast(:rangeFrom as timestamptz)";
      w.params.put("rangeFrom", EntryRepository.odt(filter.from()));
    }
    if (filter.to() != null) {
      upper = "cast(:rangeTo as timestamptz)";
      w.params.put("rangeTo", EntryRepository.odt(filter.to()));
    }
    w.and("e.occurred_range && tstzrange(" + lower + ", " + upper + ", '[)')");
    return jdbc.sql(ROW_SELECT + w.sql() + ORDER)
        .params(w.params)
        .query(TimelineRepository::row)
        .list();
  }

  // ---- tags des entrées de la page

  /** Identifiants de tags par entrée, en une seule requête. */
  public Map<UUID, List<UUID>> tagIdsByEntry(Collection<UUID> entryIds) {
    Map<UUID, List<UUID>> byEntry = new HashMap<>();
    if (entryIds.isEmpty()) {
      return byEntry;
    }
    jdbc.sql("select entry_id, tag_id from entry_tag where entry_id = any(:ids)")
        .param("ids", entryIds.toArray(new UUID[0]))
        .query(
            rs -> {
              byEntry
                  .computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                  .add(rs.getObject(2, UUID.class));
            });
    return byEntry;
  }

  // ---- facettes (toute la fenêtre filtrée, indépendantes du curseur)

  public Map<String, Integer> typeFacet(Filter filter) {
    Where w = where(filter, true, true);
    return counts(
        "select e.type as k, count(*) as n from entry e where "
            + w.sql()
            + " group by e.type order by n desc, k",
        w);
  }

  public Map<String, Integer> emotionFacet(Filter filter) {
    Where w = where(filter, true, true);
    return counts(
        "select f.emotion_code as k, count(*) as n from entry e"
            + " join entry_feeling f on f.entry_id = e.id where "
            + w.sql()
            + " group by f.emotion_code order by n desc, k",
        w);
  }

  /** Comptes par identifiant de tag, du plus fréquent au plus rare. */
  public Map<UUID, Integer> tagFacet(Filter filter) {
    Where w = where(filter, true, true);
    Map<UUID, Integer> result = new LinkedHashMap<>();
    jdbc.sql(
            "select t.tag_id as k, count(*) as n from entry e"
                + " join entry_tag t on t.entry_id = e.id where "
                + w.sql()
                + " group by t.tag_id order by n desc, k")
        .params(w.params)
        .query(
            rs -> {
              result.put(rs.getObject("k", UUID.class), rs.getInt("n"));
            });
    return result;
  }

  private Map<String, Integer> counts(String sql, Where w) {
    Map<String, Integer> result = new LinkedHashMap<>();
    jdbc.sql(sql)
        .params(w.params)
        .query(
            rs -> {
              result.put(rs.getString("k"), rs.getInt("n"));
            });
    return result;
  }

  // ---- résumé par période (UTC)

  /** Nombre d'entrées par période et par type ; {@code unit} vaut « month » ou « year ». */
  public List<PeriodTypeCount> countsByPeriod(Filter filter, String unit) {
    Where w = where(filter, true, false);
    w.params.put("unit", unit);
    return jdbc.sql(
            "select date_trunc(cast(:unit as text), e.occurred_from at time zone 'UTC') as period,"
                + " e.type, count(*) as n from entry e where "
                + w.sql()
                + " group by 1, 2 order by 1, 2")
        .params(w.params)
        .query(
            (rs, i) ->
                new PeriodTypeCount(
                    rs.getObject("period", LocalDateTime.class),
                    rs.getString("type"),
                    rs.getInt("n")))
        .list();
  }

  /** Ressentis par période : nombre et intensité moyenne. */
  public List<PeriodFeelings> feelingsByPeriod(Filter filter, String unit) {
    Where w = where(filter, true, false);
    w.params.put("unit", unit);
    return jdbc.sql(
            "select date_trunc(cast(:unit as text), e.occurred_from at time zone 'UTC') as period,"
                + " count(*) as n, cast(avg(f.intensity) as float8) as average"
                + " from entry e join entry_feeling f on f.entry_id = e.id where "
                + w.sql()
                + " group by 1 order by 1")
        .params(w.params)
        .query(
            (rs, i) ->
                new PeriodFeelings(
                    rs.getObject("period", LocalDateTime.class),
                    rs.getInt("n"),
                    rs.getDouble("average")))
        .list();
  }

  /** Début de période en instant UTC. */
  public static Instant utc(LocalDateTime period) {
    return period.toInstant(ZoneOffset.UTC);
  }
}
