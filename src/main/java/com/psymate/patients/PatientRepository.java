package com.psymate.patients;

import com.psymate.patients.PatientDtos.FlagDto;
import com.psymate.patients.PatientDtos.PatientDto;
import com.psymate.patients.PatientDtos.TagDto;
import com.psymate.patients.PatientDtos.VersionDto;
import com.psymate.patients.api.PatientRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Accès SQL du module patients (JdbcClient). */
@Repository
class PatientRepository {

  record Ownership(UUID tenantId, UUID ownerId, boolean archived) {}

  private static final String COLS =
      "id, display_name, first_name, last_name, birth_date, pronoun, phone, email,"
          + " context::text, follow_up::text, current_version, archived_at, created_at, updated_at";

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final JsonMapper json;

  private final com.psymate.vocabulary.api.TagFacade tagFacade;

  PatientRepository(
      JdbcClient jdbc, JsonMapper json, com.psymate.vocabulary.api.TagFacade tagFacade) {
    this.jdbc = jdbc;
    this.json = json;
    this.tagFacade = tagFacade;
  }

  String toJson(Object o) {
    return json.writeValueAsString(o);
  }

  Map<String, Object> fromJson(String s) {
    return s == null ? Map.of() : json.readValue(s, MAP);
  }

  Optional<Ownership> ownership(UUID id) {
    return jdbc.sql(
            "select tenant_id, owner_id, archived_at is not null from patient where id = :id")
        .param("id", id)
        .query(
            (rs, i) ->
                new Ownership(
                    rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBoolean(3)))
        .optional();
  }

  /**
   * Tous les patients du praticien, archivés compris, par nom affiché (export du compte, M6-05).
   */
  List<PatientRef> refsOwnedBy(UUID ownerId) {
    return jdbc.sql(
            "select id, display_name, birth_date, archived_at is not null as archived from patient"
                + " where owner_id = :owner"
                + " order by f_unaccent(lower(display_name)), id")
        .param("owner", ownerId)
        .query(
            (rs, i) ->
                new PatientRef(
                    rs.getObject("id", UUID.class),
                    rs.getString("display_name"),
                    rs.getObject("birth_date", LocalDate.class),
                    rs.getBoolean("archived")))
        .list();
  }

  Optional<String> displayName(UUID id) {
    return jdbc.sql("select display_name from patient where id = :id")
        .param("id", id)
        .query(String.class)
        .optional();
  }

  Optional<LocalDate> birthDate(UUID id) {
    return jdbc.sql("select birth_date from patient where id = :id")
        .param("id", id)
        .query((rs, i) -> Optional.ofNullable(rs.getObject(1, LocalDate.class)))
        .optional()
        .flatMap(x -> x);
  }

  void insert(UUID id, UUID tenant, UUID owner, PatientDto p) {
    jdbc.sql(
            "insert into patient (id, tenant_id, owner_id, display_name, first_name, last_name,"
                + " birth_date, pronoun, phone, email, context, follow_up)"
                + " values (:id, :tenant, :owner, :dn, :fn, :ln, :bd, :pr, :ph, :em,"
                + " cast(:ctx as jsonb), cast(:fu as jsonb))")
        .param("id", id)
        .param("tenant", tenant)
        .param("owner", owner)
        .param("dn", p.displayName())
        .param("fn", p.firstName())
        .param("ln", p.lastName())
        .param("bd", p.birthDate())
        .param("pr", p.pronoun())
        .param("ph", p.phone())
        .param("em", p.email())
        .param("ctx", toJson(p.context()))
        .param("fu", toJson(p.followUp()))
        .update();
  }

  Optional<PatientDto> find(UUID id) {
    return jdbc.sql("select " + COLS + " from patient where id = :id")
        .param("id", id)
        .query(this::mapPatient)
        .optional()
        .map(p -> withRelations(List.of(p)).get(0));
  }

  /** Met à jour les colonnes éditables et incrémente current_version (retournée). */
  int update(UUID id, PatientDto p) {
    return jdbc.sql(
            "update patient set display_name = :dn, first_name = :fn, last_name = :ln,"
                + " birth_date = :bd, pronoun = :pr, phone = :ph, email = :em,"
                + " context = cast(:ctx as jsonb), follow_up = cast(:fu as jsonb),"
                + " current_version = current_version + 1, updated_at = now()"
                + " where id = :id returning current_version")
        .param("id", id)
        .param("dn", p.displayName())
        .param("fn", p.firstName())
        .param("ln", p.lastName())
        .param("bd", p.birthDate())
        .param("pr", p.pronoun())
        .param("ph", p.phone())
        .param("em", p.email())
        .param("ctx", toJson(p.context()))
        .param("fu", toJson(p.followUp()))
        .query(Integer.class)
        .single();
  }

  void insertVersion(
      UUID patientId, int version, Map<String, Object> snapshot, UUID author, String reason) {
    jdbc.sql(
            "insert into patient_version (id, patient_id, version, snapshot, author_id, reason)"
                + " values (:id, :pid, :v, cast(:s as jsonb), :a, :r)")
        .param("id", UUID.randomUUID())
        .param("pid", patientId)
        .param("v", version)
        .param("s", toJson(snapshot))
        .param("a", author)
        .param("r", reason)
        .update();
  }

  List<VersionDto> versions(UUID patientId) {
    return jdbc.sql(
            "select version, snapshot::text, reason, author_id, created_at from patient_version"
                + " where patient_id = :pid order by version")
        .param("pid", patientId)
        .query(
            (rs, i) ->
                new VersionDto(
                    rs.getInt(1),
                    fromJson(rs.getString(2)),
                    rs.getString(3),
                    rs.getObject(4, UUID.class),
                    rs.getObject(5, OffsetDateTime.class)))
        .list();
  }

  void setArchived(UUID id, boolean archived) {
    jdbc.sql(
            "update patient set archived_at = "
                + (archived ? "now()" : "null")
                + ", updated_at = now() where id = :id")
        .param("id", id)
        .update();
  }

  List<PatientDto> list(
      UUID tenant,
      UUID owner,
      String pattern,
      boolean archived,
      UUID tagId,
      String orderBy,
      int limit,
      int offset) {
    StringBuilder sql =
        new StringBuilder(
            "select " + COLS + " from patient where tenant_id = :tenant and owner_id = :owner");
    sql.append(archived ? " and archived_at is not null" : " and archived_at is null");
    if (pattern != null) {
      sql.append(" and f_unaccent(display_name) ilike f_unaccent(:pattern)");
    }
    if (tagId != null) {
      sql.append(
          " and exists (select 1 from patient_tag pt where pt.patient_id = patient.id"
              + " and pt.tag_id = :tag)");
    }
    sql.append(" order by ").append(orderBy).append(" limit :limit offset :offset");
    JdbcClient.StatementSpec spec =
        jdbc.sql(sql.toString())
            .param("tenant", tenant)
            .param("owner", owner)
            .param("limit", limit)
            .param("offset", offset);
    if (pattern != null) {
      spec = spec.param("pattern", pattern);
    }
    if (tagId != null) {
      spec = spec.param("tag", tagId);
    }
    return withRelations(spec.query(this::mapPatient).list());
  }

  boolean activeFlagExists(UUID patientId, String kind) {
    return jdbc.sql(
                "select count(*) from patient_flag where patient_id = :p and kind = :k"
                    + " and cleared_at is null")
            .param("p", patientId)
            .param("k", kind)
            .query(Integer.class)
            .single()
        > 0;
  }

  void insertFlag(UUID patientId, String kind, String reason, UUID by) {
    jdbc.sql(
            "insert into patient_flag (id, patient_id, kind, reason, set_by)"
                + " values (:id, :p, :k, :r, :by)")
        .param("id", UUID.randomUUID())
        .param("p", patientId)
        .param("k", kind)
        .param("r", reason)
        .param("by", by)
        .update();
  }

  int clearFlag(UUID patientId, String kind, UUID by) {
    return jdbc.sql(
            "update patient_flag set cleared_at = now(), cleared_by = :by"
                + " where patient_id = :p and kind = :k and cleared_at is null")
        .param("p", patientId)
        .param("k", kind)
        .param("by", by)
        .update();
  }

  void replaceTags(UUID patientId, List<UUID> tagIds) {
    jdbc.sql("delete from patient_tag where patient_id = :p and not (tag_id = any(:ids))")
        .param("p", patientId)
        .param("ids", tagIds.toArray(new UUID[0]))
        .update();
    for (UUID tagId : tagIds) {
      jdbc.sql(
              "insert into patient_tag (patient_id, tag_id) values (:p, :t)"
                  + " on conflict do nothing")
          .param("p", patientId)
          .param("t", tagId)
          .update();
    }
  }

  private PatientDto mapPatient(ResultSet rs, int i) throws SQLException {
    return new PatientDto(
        rs.getObject(1, UUID.class),
        rs.getString(2),
        rs.getString(3),
        rs.getString(4),
        rs.getObject(5, LocalDate.class),
        rs.getString(6),
        rs.getString(7),
        rs.getString(8),
        fromJson(rs.getString(9)),
        fromJson(rs.getString(10)),
        List.of(),
        List.of(),
        rs.getInt(11),
        rs.getObject(12, OffsetDateTime.class),
        rs.getObject(13, OffsetDateTime.class),
        rs.getObject(14, OffsetDateTime.class));
  }

  private List<PatientDto> withRelations(List<PatientDto> patients) {
    if (patients.isEmpty()) {
      return patients;
    }
    UUID[] ids = patients.stream().map(PatientDto::id).toArray(UUID[]::new);
    Map<UUID, List<FlagDto>> flags = new HashMap<>();
    jdbc.sql(
            "select patient_id, kind, reason, set_at from patient_flag"
                + " where patient_id = any(:ids) and cleared_at is null order by set_at")
        .param("ids", ids)
        .query(
            (rs) -> {
              flags
                  .computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                  .add(
                      new FlagDto(
                          rs.getString(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class)));
            });
    Map<UUID, List<UUID>> tagIdsByPatient = new HashMap<>();
    jdbc.sql("select patient_id, tag_id from patient_tag where patient_id = any(:ids)")
        .param("ids", ids)
        .query(
            (rs) -> {
              tagIdsByPatient
                  .computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                  .add(rs.getObject(2, UUID.class));
            });
    Map<UUID, String> labels =
        tagFacade.labelsByIds(
            tagIdsByPatient.values().stream().flatMap(List::stream).distinct().toList());
    Map<UUID, List<TagDto>> tags = new HashMap<>();
    tagIdsByPatient.forEach(
        (pid, tids) ->
            tags.put(
                pid,
                tids.stream()
                    .filter(labels::containsKey)
                    .map(t -> new TagDto(t, labels.get(t)))
                    .sorted(java.util.Comparator.comparing(TagDto::label))
                    .toList()));
    List<PatientDto> out = new ArrayList<>();
    for (PatientDto p : patients) {
      out.add(
          new PatientDto(
              p.id(),
              p.displayName(),
              p.firstName(),
              p.lastName(),
              p.birthDate(),
              p.pronoun(),
              p.phone(),
              p.email(),
              p.context(),
              p.followUp(),
              flags.getOrDefault(p.id(), List.of()),
              tags.getOrDefault(p.id(), List.of()),
              p.currentVersion(),
              p.archivedAt(),
              p.createdAt(),
              p.updatedAt()));
    }
    return out;
  }

  void touchClinicalRev(UUID patientId) {
    jdbc.sql("update patient set clinical_rev = clinical_rev + 1 where id = :id")
        .param("id", patientId)
        .update();
  }
}
