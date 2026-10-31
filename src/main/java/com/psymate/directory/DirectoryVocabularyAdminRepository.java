package com.psymate.directory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accès JDBC à l'administration du vocabulaire de l'annuaire (G Q15, M1-14b) : directory_vocabulary
 * et practitioner_profile, deux tables du module directory.
 */
@Repository
class DirectoryVocabularyAdminRepository {

  /** Entrée du vocabulaire avec le nombre de fiches publiées qui l'utilisent. */
  record Row(
      String kind,
      String code,
      String labelFr,
      String labelEn,
      int sortOrder,
      boolean active,
      int usedByPublishedProfiles) {}

  private static final String USED =
      "(select count(*) from practitioner_profile p where p.published and"
          + " case v.kind when 'specialty' then v.code = any(p.specialties)"
          + " else v.code = any(p.audiences) end)";

  private static final String SELECT =
      "select v.kind, v.code, v.label_fr, v.label_en, v.sort_order, v.active, "
          + USED
          + " as used from directory_vocabulary v";

  private final JdbcClient jdbc;

  DirectoryVocabularyAdminRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static Row map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
    return new Row(
        rs.getString("kind"),
        rs.getString("code"),
        rs.getString("label_fr"),
        rs.getString("label_en"),
        rs.getInt("sort_order"),
        rs.getBoolean("active"),
        rs.getInt("used"));
  }

  /** Toutes les entrées, actives ou non, d'un type ou des deux. */
  List<Row> list(String kind) {
    if (kind == null) {
      return jdbc.sql(SELECT + " order by v.kind, v.sort_order, v.label_fr")
          .query(DirectoryVocabularyAdminRepository::map)
          .list();
    }
    return jdbc.sql(SELECT + " where v.kind = :kind order by v.sort_order, v.label_fr")
        .param("kind", kind)
        .query(DirectoryVocabularyAdminRepository::map)
        .list();
  }

  Optional<Row> find(String kind, String code) {
    return jdbc.sql(SELECT + " where v.kind = :kind and v.code = :code")
        .param("kind", kind)
        .param("code", code)
        .query(DirectoryVocabularyAdminRepository::map)
        .optional();
  }

  /** Verrouille l'entrée le temps de la mise à jour. */
  boolean lock(String kind, String code) {
    return !jdbc.sql(
            "select code from directory_vocabulary where kind = :kind and code = :code for update")
        .param("kind", kind)
        .param("code", code)
        .query((rs, i) -> rs.getString(1))
        .list()
        .isEmpty();
  }

  boolean exists(String kind, String code) {
    return jdbc.sql("select count(*) from directory_vocabulary where kind = :kind and code = :code")
            .param("kind", kind)
            .param("code", code)
            .query(Integer.class)
            .single()
        > 0;
  }

  /** Rang suivant le plus grand du type (0 si le type est vide), borné à smallint. */
  int nextSortOrder(String kind) {
    return jdbc.sql(
            "select coalesce(max(sort_order), 0) + 1 from directory_vocabulary where kind = :kind")
        .param("kind", kind)
        .query(Integer.class)
        .single();
  }

  void insert(
      String kind, String code, String labelFr, String labelEn, int sortOrder, Instant now) {
    jdbc.sql(
            "insert into directory_vocabulary (kind, code, label_fr, label_en, sort_order, active,"
                + " created_at, updated_at) values (:kind, :code, :fr, :en, :sort, true, :now, :now)")
        .param("kind", kind)
        .param("code", code)
        .param("fr", labelFr)
        .param("en", labelEn)
        .param("sort", (short) sortOrder)
        .param("now", Timestamp.from(now))
        .update();
  }

  void update(
      String kind,
      String code,
      String labelFr,
      String labelEn,
      int sortOrder,
      boolean active,
      Instant now) {
    jdbc.sql(
            "update directory_vocabulary set label_fr = :fr, label_en = :en, sort_order = :sort,"
                + " active = :active, updated_at = :now where kind = :kind and code = :code")
        .param("fr", labelFr)
        .param("en", labelEn)
        .param("sort", (short) sortOrder)
        .param("active", active)
        .param("now", Timestamp.from(now))
        .param("kind", kind)
        .param("code", code)
        .update();
  }
}
