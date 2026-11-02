package com.psymate.search.repository;

import com.psymate.search.model.SnippetBuilder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accès SQL de la recherche : lecture directe de entry, entry_version, entry_feeling, entry_tag,
 * tag, emotion et entry_type (projection de lecture, A5). Toutes les requêtes sont bornées au
 * cabinet et au patient, et excluent les entrées privées d'un autre praticien.
 */
@Repository
public class EntrySearchRepository {

  public static final int PAGE_SIZE = 20;
  static final double SIMILARITY_THRESHOLD = 0.3;

  private static final String HEADLINE_BODY =
      "'MaxFragments=2, MinWords=5, MaxWords=18, StartSel="
          + SnippetBuilder.OPEN
          + ", StopSel="
          + SnippetBuilder.CLOSE
          + "'";
  private static final String HEADLINE_TITLE =
      "'HighlightAll=true, StartSel="
          + SnippetBuilder.OPEN
          + ", StopSel="
          + SnippetBuilder.CLOSE
          + "'";

  /** Boost x1,5 des entrées des 90 derniers jours (A6). */
  private static final String BOOST =
      "case when e.occurred_from >= now() - interval '90 days' then 1.5 else 1 end";

  /**
   * Requête tsquery. Le vecteur mêle des lexèmes « french » (titre, corps) et « simple » (émotion,
   * tags, molécule) : la requête est donc construite dans les deux configurations, sans quoi «
   * anxiété » (lexème simple « anxiete ») ne trouverait jamais l'émotion, le radical français étant
   * « anxiet ». Une négation ({@code -mot}) ne se combine pas en « ou » (la branche simple la
   * satisferait trivialement) : dans ce cas, french seul.
   */
  private static final String TSQUERY_BOTH =
      "q as (select (websearch_to_tsquery('french', f_unaccent(:text))"
          + " || websearch_to_tsquery('simple', f_unaccent(:text))) as tsq)";

  private static final String TSQUERY_FRENCH =
      "q as (select websearch_to_tsquery('french', f_unaccent(:text)) as tsq)";

  private static final Pattern NEGATION = Pattern.compile("(^|\\s)-\\S");

  private static String tsquery(Filter f) {
    return f.negated() ? TSQUERY_FRENCH : TSQUERY_BOTH;
  }

  /** Corps sans les délimiteurs de surlignage (ils ne doivent jamais venir du texte). */
  private static final String CLEAN_BODY =
      "translate(h.body, '" + SnippetBuilder.OPEN + SnippetBuilder.CLOSE + "', '')";

  /** Titre sans les délimiteurs de surlignage, pour la même raison. */
  private static final String CLEAN_TITLE =
      "translate(h.title, '" + SnippetBuilder.OPEN + SnippetBuilder.CLOSE + "', '')";

  private static final String NO_HIGHLIGHT =
      "null::text as marked_title, null::text as marked_snippet,"
          + " null::text as marked_title_folded, null::text as marked_snippet_folded";

  /** Texte comparé par le repli trigramme : même expression que l'index de V08. */
  private static final String TRGM_TEXT =
      "f_unaccent(coalesce(v.title, '') || ' ' || coalesce(v.body, ''))";

  private static final String HIT_COLUMNS =
      "e.id, e.type, e.occurred_from, e.occurred_to, e.occurred_precision, e.session_id,"
          + " e.visibility, e.archived_at, e.current_version as version, v.title,"
          + " ef.emotion_code";

  private static final String HIT_FROM =
      " from entry e join entry_version v on v.id = e.current_version_id"
          + " left join entry_feeling ef on ef.entry_id = e.id";

  /** Dimension d'une facette, dont le propre filtre est ignoré. */
  enum Dimension {
    NONE,
    TYPES,
    EMOTIONS,
    TAGS
  }

  /**
   * Filtre résolu. Une liste nulle signifie « pas de filtre » ; une liste vide « filtre actif qui
   * ne retient rien » (tag ou émotion inconnus). {@code text} est vide sans recherche textuelle.
   */
  public record Filter(
      UUID tenant,
      UUID patient,
      UUID me,
      String text,
      List<String> types,
      List<UUID> tagIds,
      List<String> emotionCodes,
      Instant lower,
      Instant upperExclusive,
      UUID sessionId,
      boolean includeArchived) {

    public boolean hasText() {
      return text != null && !text.isBlank();
    }

    /** Le texte contient une négation websearch ({@code -mot}). */
    public boolean negated() {
      return hasText() && NEGATION.matcher(text).find();
    }
  }

  /** Une entrée trouvée, telle que lue en base (titre et extrait encore à convertir en HTML). */
  public record Hit(
      UUID id,
      String type,
      Instant occurredFrom,
      Instant occurredTo,
      String occurredPrecision,
      UUID sessionId,
      String visibility,
      Instant archivedAt,
      int version,
      String title,
      String emotionCode,
      String body,
      String markedTitle,
      String markedSnippet,
      String markedTitleFolded,
      String markedSnippetFolded,
      Double rank,
      Double similarity) {}

  public record TypeCount(String code, int count) {}

  public record EmotionCount(String code, String label, int count) {}

  public record TagCount(UUID id, String label, int count) {}

  private record Clause(String sql, Map<String, Object> params) {}

  private final JdbcClient jdbc;

  EntrySearchRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  // ---- résolution des valeurs saisies

  public List<String> entryTypeCodes() {
    return jdbc.sql("select code from entry_type").query(String.class).list();
  }

  /** Tags du cabinet dont le libellé normalisé (sans accents ni casse) est l'un de ceux-ci. */
  public List<UUID> resolveTags(UUID tenant, List<String> labels) {
    return jdbc.sql(
            "select t.id from tag t where t.tenant_id = :tenant and t.normalized_label in"
                + " (select lower(f_unaccent(x)) from unnest(cast(:labels as text[])) as x)")
        .param("tenant", tenant)
        .param("labels", labels.toArray(String[]::new))
        .query((rs, i) -> rs.getObject("id", UUID.class))
        .list();
  }

  /** Émotions dont le code ou le libellé (fr, en, normalisé) est l'une de ces valeurs. */
  public List<String> resolveEmotions(UUID tenant, List<String> values) {
    return jdbc.sql(
            "with n as (select lower(f_unaccent(x)) as v from unnest(cast(:vals as text[])) as x)"
                + " select em.code from emotion em"
                + " where (em.scope = 'global' or em.tenant_id = :tenant)"
                + " and exists (select 1 from n where n.v = lower(em.code)"
                + " or n.v = lower(f_unaccent(em.label_fr))"
                + " or n.v = lower(f_unaccent(coalesce(em.label_en, '')))"
                + " or n.v = em.label_normalized)")
        .param("tenant", tenant)
        .param("vals", values.toArray(String[]::new))
        .query(String.class)
        .list();
  }

  // ---- filtre

  private static OffsetDateTime odt(Instant i) {
    return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
  }

  /** Conditions communes (hors texte), sans le filtre de la dimension ignorée. */
  private static Clause where(Filter f, Dimension skip) {
    Map<String, Object> p = new HashMap<>();
    StringBuilder sb =
        new StringBuilder(
            "e.tenant_id = :tenant and e.patient_id = :patient"
                + " and (e.visibility = 'clinical' or e.created_by = :me)");
    p.put("tenant", f.tenant());
    p.put("patient", f.patient());
    p.put("me", f.me());
    if (!f.includeArchived()) {
      sb.append(" and e.archived_at is null");
    }
    if (f.types() != null && skip != Dimension.TYPES) {
      sb.append(" and e.type = any(cast(:types as text[]))");
      p.put("types", f.types().toArray(String[]::new));
    }
    if (f.tagIds() != null && skip != Dimension.TAGS) {
      sb.append(
          " and exists (select 1 from entry_tag ft where ft.entry_id = e.id"
              + " and ft.tag_id = any(cast(:tagIds as uuid[])))");
      p.put("tagIds", f.tagIds().stream().map(UUID::toString).toArray(String[]::new));
    }
    if (f.emotionCodes() != null && skip != Dimension.EMOTIONS) {
      sb.append(
          " and exists (select 1 from entry_feeling ff where ff.entry_id = e.id"
              + " and ff.emotion_code = any(cast(:emotionCodes as text[])))");
      p.put("emotionCodes", f.emotionCodes().toArray(String[]::new));
    }
    if (f.lower() != null) {
      sb.append(" and e.occurred_from >= :lower");
      p.put("lower", odt(f.lower()));
    }
    if (f.upperExclusive() != null) {
      sb.append(" and e.occurred_from < :upper");
      p.put("upper", odt(f.upperExclusive()));
    }
    if (f.sessionId() != null) {
      sb.append(" and e.session_id = :sessionId");
      p.put("sessionId", f.sessionId());
    }
    return new Clause(sb.toString(), p);
  }

  private <T> List<T> run(String sql, Map<String, Object> params, RowMapper<T> mapper) {
    return jdbc.sql(sql).params(params).query(mapper).list();
  }

  // ---- résultats exacts

  /** Nombre d'entrées retenues par le filtre (et par le texte, s'il y en a un). */
  public long countExact(Filter f) {
    Clause c = where(f, Dimension.NONE);
    Map<String, Object> params = new HashMap<>(c.params());
    String sql;
    if (f.hasText()) {
      params.put("text", f.text());
      sql =
          "with "
              + tsquery(f)
              + " select count(*) from entry e, q where "
              + c.sql()
              + " and e.search_vector @@ q.tsq";
    } else {
      sql = "select count(*) from entry e where " + c.sql();
    }
    return run(sql, params, (rs, i) -> rs.getLong(1)).get(0);
  }

  /** Page de résultats exacts : classés par pertinence avec texte, par récence sans texte. */
  public List<Hit> findExact(Filter f, int page) {
    Clause c = where(f, Dimension.NONE);
    Map<String, Object> params = new HashMap<>(c.params());
    params.put("limit", PAGE_SIZE);
    params.put("offset", (page - 1) * PAGE_SIZE);
    if (!f.hasText()) {
      String sql =
          "select "
              + HIT_COLUMNS
              + ", left(v.body, 400) as body, "
              + NO_HIGHLIGHT
              + ", null::float8 as rank, null::float8 as sim"
              + HIT_FROM
              + " where "
              + c.sql()
              + " order by e.occurred_from desc, e.id limit :limit offset :offset";
      return run(sql, params, EntrySearchRepository::hit);
    }
    params.put("text", f.text());
    // Le titre et l'extrait ne sont surlignés que pour la page (ts_headline est coûteux) : la
    // sous-requête hits porte le tri et la limite.
    String sql =
        "with "
            + tsquery(f)
            + ", hits as (select "
            + HIT_COLUMNS
            + ", v.body, ts_rank_cd(e.search_vector, q.tsq) * "
            + BOOST
            + " as rank"
            + HIT_FROM
            + ", q where "
            + c.sql()
            + " and e.search_vector @@ q.tsq"
            + " order by rank desc, e.occurred_from desc, e.id limit :limit offset :offset)"
            + " select h.id, h.type, h.occurred_from, h.occurred_to, h.occurred_precision,"
            + " h.session_id, h.visibility, h.archived_at, h.version, "
            + CLEAN_TITLE
            + " as title, h.emotion_code, "
            + CLEAN_BODY
            + " as body,"
            + " case when h.title is null then null else ts_headline('french', "
            + CLEAN_TITLE
            + ", q.tsq, "
            + HEADLINE_TITLE
            + ") end as marked_title,"
            + " case when h.body is null then null else ts_headline('french',"
            + CLEAN_BODY
            + ", q.tsq, "
            + HEADLINE_BODY
            + ") end as marked_snippet,"
            // Même surlignage sur le texte sans accents : le radical français d'un mot accentué
            // diffère de celui du mot sans accent, que l'index et la requête utilisent.
            + " case when h.title is null then null else ts_headline('french',"
            + " f_unaccent("
            + CLEAN_TITLE
            + "), q.tsq, "
            + HEADLINE_TITLE
            + ") end as marked_title_folded,"
            + " case when h.body is null then null else ts_headline('french',"
            + " f_unaccent("
            + CLEAN_BODY
            + "), q.tsq, "
            + HEADLINE_BODY
            + ") end as marked_snippet_folded,"
            + " h.rank, null::float8 as sim"
            + " from hits h, q order by h.rank desc, h.occurred_from desc, h.id";
    return run(sql, params, EntrySearchRepository::hit);
  }

  /**
   * Résultats approchés (repli trigramme) : entrées du même filtre que la recherche exacte qui ne
   * sont pas des résultats exacts mais dont un mot ressemble au texte saisi.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<Hit> findApproximate(Filter f) {
    // Seuil de l'opérateur <% (index ix_entry_version_text_trgm), limité à la transaction.
    jdbc.sql("select set_config('pg_trgm.word_similarity_threshold', :threshold, true)")
        .param("threshold", Double.toString(SIMILARITY_THRESHOLD))
        .query()
        .listOfRows();
    Clause c = where(f, Dimension.NONE);
    Map<String, Object> params = new HashMap<>(c.params());
    params.put("text", f.text());
    params.put("simText", f.text().replace('"', ' ').strip());
    params.put("limit", PAGE_SIZE);
    String sql =
        "with "
            + tsquery(f)
            + " select s.* from (select "
            + HIT_COLUMNS
            + ", v.body as body, "
            + NO_HIGHLIGHT
            + ", null::float8 as rank, word_similarity(f_unaccent(:simText), "
            + TRGM_TEXT
            + ") as sim"
            + HIT_FROM
            + ", q where "
            + c.sql()
            + " and f_unaccent(:simText) <% "
            + TRGM_TEXT
            + " and not coalesce(e.search_vector @@ q.tsq, false)) s"
            + " where s.sim > "
            + SIMILARITY_THRESHOLD
            + " order by s.sim desc, s.occurred_from desc, s.id limit :limit";
    return run(sql, params, EntrySearchRepository::hit);
  }

  private static Hit hit(ResultSet rs, int row) throws SQLException {
    return new Hit(
        rs.getObject("id", UUID.class),
        rs.getString("type"),
        instant(rs, "occurred_from"),
        instant(rs, "occurred_to"),
        rs.getString("occurred_precision"),
        rs.getObject("session_id", UUID.class),
        rs.getString("visibility"),
        instant(rs, "archived_at"),
        rs.getInt("version"),
        rs.getString("title"),
        rs.getString("emotion_code"),
        rs.getString("body"),
        rs.getString("marked_title"),
        rs.getString("marked_snippet"),
        rs.getString("marked_title_folded"),
        rs.getString("marked_snippet_folded"),
        number(rs, "rank"),
        number(rs, "sim"));
  }

  /** float4 (word_similarity) ou float8 (rang) selon la colonne : toujours un Double. */
  private static Double number(ResultSet rs, String column) throws SQLException {
    Object value = rs.getObject(column);
    return value == null ? null : ((Number) value).doubleValue();
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
    return t == null ? null : t.toInstant();
  }

  /** Libellés des tags des entrées données, triés par libellé. */
  public Map<UUID, List<String>> tagLabels(List<UUID> entryIds) {
    Map<UUID, List<String>> byEntry = new HashMap<>();
    if (entryIds.isEmpty()) {
      return byEntry;
    }
    jdbc.sql(
            "select et.entry_id, t.label from entry_tag et join tag t on t.id = et.tag_id"
                + " where et.entry_id = any(cast(:ids as uuid[])) order by t.label")
        .param("ids", entryIds.stream().map(UUID::toString).toArray(String[]::new))
        .query(
            rs -> {
              byEntry
                  .computeIfAbsent(rs.getObject("entry_id", UUID.class), k -> new ArrayList<>())
                  .add(rs.getString("label"));
            });
    return byEntry;
  }

  // ---- facettes (sur les résultats exacts, hors filtre de leur propre dimension)

  public List<TypeCount> typeFacets(Filter f) {
    return facet(
        f,
        Dimension.TYPES,
        "select e.type as k, e.type as label, count(*) as n from entry e",
        "",
        " group by e.type order by n desc, e.type",
        (rs, i) -> new TypeCount(rs.getString("k"), rs.getInt("n")));
  }

  public List<EmotionCount> emotionFacets(Filter f) {
    return facet(
        f,
        Dimension.EMOTIONS,
        "select em.code as k, em.label_fr as label, count(*) as n from entry e",
        " join entry_feeling fe on fe.entry_id = e.id join emotion em on em.code = fe.emotion_code",
        " group by em.code, em.label_fr order by n desc, em.label_fr, em.code",
        (rs, i) -> new EmotionCount(rs.getString("k"), rs.getString("label"), rs.getInt("n")));
  }

  public List<TagCount> tagFacets(Filter f) {
    return facet(
        f,
        Dimension.TAGS,
        "select t.id::text as k, t.label as label, count(*) as n from entry e",
        " join entry_tag fe on fe.entry_id = e.id join tag t on t.id = fe.tag_id",
        " group by t.id, t.label order by n desc, t.label, t.id",
        (rs, i) ->
            new TagCount(
                UUID.fromString(rs.getString("k")), rs.getString("label"), rs.getInt("n")));
  }

  private <T> List<T> facet(
      Filter f, Dimension skip, String select, String join, String groupBy, RowMapper<T> mapper) {
    Clause c = where(f, skip);
    Map<String, Object> params = new HashMap<>(c.params());
    String sql;
    if (f.hasText()) {
      params.put("text", f.text());
      sql =
          "with "
              + tsquery(f)
              + " "
              + select
              + join
              + ", q where "
              + c.sql()
              + " and e.search_vector @@ q.tsq"
              + groupBy;
    } else {
      sql = select + join + " where " + c.sql() + groupBy;
    }
    return run(sql, params, mapper);
  }
}
