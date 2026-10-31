package com.psymate.vocabulary;

import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.EntryTypeView;
import com.psymate.vocabulary.api.LinkTypeView;
import com.psymate.vocabulary.api.TagView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Accès SQL du module (JdbcClient). Ne lit que les tables du module, plus entry_* si présentes. */
@Repository
class VocabularyRepository {

  record MergeRow(
      UUID id,
      UUID practitionerId,
      String sourceCode,
      String targetCode,
      int affectedCount,
      OffsetDateTime mergedAt,
      OffsetDateTime undoUntil,
      OffsetDateTime undoneAt) {}

  private static final String EMOTION_COLS =
      "code, scope, family, intensity_level, label_fr, label_en, active";

  private static final String TAG_COLS = "id, label, normalized_label, usage_count, created_at";

  private final JdbcClient jdbc;

  VocabularyRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  // ---- entry types, link types

  List<EntryTypeView> entryTypes() {
    return jdbc.sql(
            "select code, label_fr, label_en, detail_table, has_duration, sort_order"
                + " from entry_type where active order by sort_order")
        .query(
            (rs, i) ->
                new EntryTypeView(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getBoolean(5),
                    rs.getInt(6)))
        .list();
  }

  List<LinkTypeView> linkTypes() {
    return jdbc.sql(
            "select code, directed, label_fr, label_en, inverse_label_fr, inverse_label_en,"
                + " allowed_source_types, allowed_target_types, line_style, sort_order"
                + " from link_type where active order by sort_order")
        .query(VocabularyRepository::linkType)
        .list();
  }

  private static LinkTypeView linkType(ResultSet rs, int i) throws SQLException {
    return new LinkTypeView(
        rs.getString(1),
        rs.getBoolean(2),
        rs.getString(3),
        rs.getString(4),
        rs.getString(5),
        rs.getString(6),
        Arrays.asList((String[]) rs.getArray(7).getArray()),
        Arrays.asList((String[]) rs.getArray(8).getArray()),
        rs.getString(9),
        rs.getInt(10));
  }

  // ---- emotions

  private static EmotionView emotion(ResultSet rs, int i) throws SQLException {
    int level = rs.getInt(4);
    Integer intensity = rs.wasNull() ? null : level;
    return new EmotionView(
        rs.getString(1),
        rs.getString(2),
        rs.getString(3),
        intensity,
        rs.getString(5),
        rs.getString(6),
        rs.getBoolean(7));
  }

  List<EmotionView> activeEmotions(UUID practitionerId) {
    return jdbc.sql(
            "select "
                + EMOTION_COLS
                + " from emotion where active and (scope = 'global' or practitioner_id = :p)"
                + " order by case scope when 'global' then 0 else 1 end, family,"
                + " intensity_level nulls last, label_fr")
        .param("p", practitionerId)
        .query(VocabularyRepository::emotion)
        .list();
  }

  Optional<EmotionView> activeEmotionByNormalized(UUID practitionerId, String normalized) {
    return jdbc.sql(
            "select "
                + EMOTION_COLS
                + " from emotion where active and label_normalized = :n"
                + " and (scope = 'global' or practitioner_id = :p)"
                + " order by case scope when 'global' then 0 else 1 end limit 1")
        .param("n", normalized)
        .param("p", practitionerId)
        .query(VocabularyRepository::emotion)
        .optional();
  }

  /** Libellé déjà pris par une globale ou une custom du praticien (active ou non). */
  boolean emotionLabelTaken(UUID practitionerId, String normalized, String excludingCode) {
    String exclude = excludingCode == null ? "" : excludingCode;
    return jdbc.sql(
                "select count(*) from emotion where label_normalized = :n"
                    + " and (scope = 'global' or practitioner_id = :p)"
                    + " and code <> :x")
            .param("n", normalized)
            .param("p", practitionerId)
            .param("x", exclude)
            .query(Integer.class)
            .single()
        > 0;
  }

  void insertCustomEmotion(
      String code, UUID tenantId, UUID practitionerId, String family, String labelFr, String norm) {
    jdbc.sql(
            "insert into emotion (code, scope, tenant_id, practitioner_id, family, label_fr,"
                + " label_normalized, created_by) values (:c, 'custom', :t, :p, :f, :l, :n, :p)")
        .param("c", code)
        .param("t", tenantId)
        .param("p", practitionerId)
        .param("f", family)
        .param("l", labelFr)
        .param("n", norm)
        .update();
  }

  /** Custom du praticien (active ou non). */
  Optional<EmotionView> ownCustomEmotion(UUID practitionerId, String code) {
    return jdbc.sql(
            "select "
                + EMOTION_COLS
                + " from emotion where code = :c and scope = 'custom' and practitioner_id = :p")
        .param("c", code)
        .param("p", practitionerId)
        .query(VocabularyRepository::emotion)
        .optional();
  }

  Optional<EmotionView> usableEmotion(UUID practitionerId, String code) {
    return jdbc.sql(
            "select "
                + EMOTION_COLS
                + " from emotion where code = :c and active"
                + " and (scope = 'global' or practitioner_id = :p)")
        .param("c", code)
        .param("p", practitionerId)
        .query(VocabularyRepository::emotion)
        .optional();
  }

  void renameEmotion(String code, String labelFr, String norm) {
    jdbc.sql("update emotion set label_fr = :l, label_normalized = :n where code = :c")
        .param("l", labelFr)
        .param("n", norm)
        .param("c", code)
        .update();
  }

  void setEmotionActive(String code, boolean active) {
    jdbc.sql("update emotion set active = :a where code = :c")
        .param("a", active)
        .param("c", code)
        .update();
  }

  private boolean tableExists(String table) {
    return jdbc.sql("select to_regclass(:t) is not null")
        .param("t", "public." + table)
        .query(Boolean.class)
        .single();
  }

  /**
   * Réécrit entry_feeling (M1-03) : emotion_code passe à la cible, original_emotion_code garde la
   * source. Sans effet tant que la table n'existe pas. Retourne le nombre de lignes touchées.
   */
  int rewriteFeelings(String source, String target) {
    if (!tableExists("entry_feeling")) {
      return 0;
    }
    return jdbc.sql(
            "update entry_feeling set original_emotion_code = :s, emotion_code = :t"
                + " where emotion_code = :s")
        .param("s", source)
        .param("t", target)
        .update();
  }

  /** Défait la réécriture : remet emotion_code = source pour les lignes issues de la fusion. */
  int restoreFeelings(String source, String target) {
    if (!tableExists("entry_feeling")) {
      return 0;
    }
    return jdbc.sql(
            "update entry_feeling set emotion_code = :s, original_emotion_code = null"
                + " where original_emotion_code = :s and emotion_code = :t")
        .param("s", source)
        .param("t", target)
        .update();
  }

  void insertMerge(
      UUID id, UUID practitionerId, String source, String target, int affected, int undoDays) {
    jdbc.sql(
            "insert into emotion_merge (id, practitioner_id, source_code, target_code,"
                + " affected_count, undo_until)"
                + " values (:id, :p, :s, :t, :a, now() + make_interval(days => :d))")
        .param("id", id)
        .param("p", practitionerId)
        .param("s", source)
        .param("t", target)
        .param("a", affected)
        .param("d", undoDays)
        .update();
  }

  Optional<MergeRow> merge(UUID id, UUID practitionerId) {
    return jdbc.sql(
            "select id, practitioner_id, source_code, target_code, affected_count, merged_at,"
                + " undo_until, undone_at from emotion_merge"
                + " where id = :id and practitioner_id = :p")
        .param("id", id)
        .param("p", practitionerId)
        .query(
            (rs, i) ->
                new MergeRow(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getInt(5),
                    rs.getObject(6, OffsetDateTime.class),
                    rs.getObject(7, OffsetDateTime.class),
                    rs.getObject(8, OffsetDateTime.class)))
        .optional();
  }

  boolean mergeStillOpen(UUID id) {
    return jdbc.sql(
            "select undo_until > now() and undone_at is null from emotion_merge where id = :id")
        .param("id", id)
        .query(Boolean.class)
        .single();
  }

  void markMergeUndone(UUID id) {
    jdbc.sql("update emotion_merge set undone_at = now() where id = :id").param("id", id).update();
  }

  // ---- tags

  private static TagView tag(ResultSet rs, int i) throws SQLException {
    return new TagView(
        rs.getObject(1, UUID.class),
        rs.getString(2),
        rs.getString(3),
        rs.getInt(4),
        rs.getObject(5, OffsetDateTime.class));
  }

  List<TagView> activeTags(UUID practitionerId) {
    return jdbc.sql(
            "select "
                + TAG_COLS
                + " from tag where practitioner_id = :p and archived_at is null"
                + " order by normalized_label")
        .param("p", practitionerId)
        .query(VocabularyRepository::tag)
        .list();
  }

  Optional<TagView> activeTagByNormalized(UUID practitionerId, String normalized) {
    return jdbc.sql(
            "select "
                + TAG_COLS
                + " from tag where practitioner_id = :p and normalized_label = :n"
                + " and archived_at is null")
        .param("p", practitionerId)
        .param("n", normalized)
        .query(VocabularyRepository::tag)
        .optional();
  }

  Optional<TagView> activeTag(UUID practitionerId, UUID id) {
    return jdbc.sql(
            "select "
                + TAG_COLS
                + " from tag where id = :id and practitioner_id = :p and archived_at is null")
        .param("id", id)
        .param("p", practitionerId)
        .query(VocabularyRepository::tag)
        .optional();
  }

  void insertTag(UUID id, UUID tenantId, UUID practitionerId, String label, String norm) {
    jdbc.sql(
            "insert into tag (id, tenant_id, practitioner_id, label, normalized_label)"
                + " values (:id, :t, :p, :l, :n)")
        .param("id", id)
        .param("t", tenantId)
        .param("p", practitionerId)
        .param("l", label)
        .param("n", norm)
        .update();
  }

  void renameTag(UUID id, String label, String norm) {
    jdbc.sql("update tag set label = :l, normalized_label = :n where id = :id")
        .param("l", label)
        .param("n", norm)
        .param("id", id)
        .update();
  }

  /**
   * Réétiquette vers la cible : entry_tag (M1-03, ignoré tant que la table n'existe pas) et
   * patient_tag, sans doublon ; archive la source et additionne usage_count.
   */
  void mergeTags(UUID source, UUID target) {
    if (tableExists("entry_tag")) {
      jdbc.sql(
              "delete from entry_tag where tag_id = :s and entry_id in"
                  + " (select entry_id from entry_tag where tag_id = :t)")
          .param("s", source)
          .param("t", target)
          .update();
      jdbc.sql("update entry_tag set tag_id = :t where tag_id = :s")
          .param("s", source)
          .param("t", target)
          .update();
    }
    jdbc.sql(
            "delete from patient_tag where tag_id = :s and patient_id in"
                + " (select patient_id from patient_tag where tag_id = :t)")
        .param("s", source)
        .param("t", target)
        .update();
    jdbc.sql("update patient_tag set tag_id = :t where tag_id = :s")
        .param("s", source)
        .param("t", target)
        .update();
    jdbc.sql(
            "update tag set usage_count = usage_count"
                + " + (select usage_count from tag where id = :s) where id = :t")
        .param("s", source)
        .param("t", target)
        .update();
    jdbc.sql("update tag set archived_at = now(), merged_into = :t where id = :s")
        .param("s", source)
        .param("t", target)
        .update();
  }
}
