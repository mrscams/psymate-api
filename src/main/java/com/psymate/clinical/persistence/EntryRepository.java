package com.psymate.clinical.persistence;

import com.psymate.clinical.api.SessionView;
import com.psymate.clinical.domain.Entry;
import com.psymate.clinical.domain.EntryStatus;
import com.psymate.clinical.domain.EntryType;
import com.psymate.clinical.domain.EntryVersion;
import com.psymate.clinical.domain.FeelingDetail;
import com.psymate.clinical.domain.MedicationDetail;
import com.psymate.clinical.domain.SessionDetail;
import com.psymate.clinical.domain.TimeSpan;
import com.psymate.clinical.domain.Visibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Accès SQL du module clinical (JdbcClient) : entry, entry_version et tables de détail. */
@Repository
public class EntryRepository {

  private static final String ENTRY_COLS =
      "id, tenant_id, patient_id, type, status, visibility, occurred_from, occurred_to,"
          + " occurred_precision, occurred_label, session_id, current_version_id, current_version,"
          + " link_count, author_type, archived_at, created_by, created_at, updated_at";

  private static final String VERSION_COLS =
      "id, entry_id, version, title, body, payload::text as payload, reason, author_id,"
          + " created_at";

  private final JdbcClient jdbc;

  EntryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  static OffsetDateTime odt(Instant i) {
    return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet rs, String col) throws SQLException {
    OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
    return o == null ? null : o.toInstant();
  }

  private static UUID uuid(ResultSet rs, String col) throws SQLException {
    return rs.getObject(col, UUID.class);
  }

  private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
    int v = rs.getInt(col);
    return rs.wasNull() ? null : v;
  }

  private static Entry entry(ResultSet rs, int i) throws SQLException {
    TimeSpan time =
        new TimeSpan(
            instant(rs, "occurred_from"),
            instant(rs, "occurred_to"),
            TimeSpan.Precision.fromCode(rs.getString("occurred_precision")),
            rs.getString("occurred_label"));
    return new Entry(
        uuid(rs, "id"),
        uuid(rs, "tenant_id"),
        uuid(rs, "patient_id"),
        EntryType.fromCode(rs.getString("type")).orElseThrow(),
        EntryStatus.fromCode(rs.getString("status")),
        Visibility.fromCode(rs.getString("visibility")),
        time,
        uuid(rs, "session_id"),
        uuid(rs, "current_version_id"),
        rs.getInt("current_version"),
        rs.getInt("link_count"),
        rs.getString("author_type"),
        instant(rs, "archived_at"),
        uuid(rs, "created_by"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private static EntryVersion version(ResultSet rs, int i) throws SQLException {
    return new EntryVersion(
        uuid(rs, "id"),
        uuid(rs, "entry_id"),
        rs.getInt("version"),
        rs.getString("title"),
        rs.getString("body"),
        rs.getString("payload"),
        rs.getString("reason"),
        uuid(rs, "author_id"),
        instant(rs, "created_at"));
  }

  // ---- lecture

  public Optional<Entry> find(UUID id) {
    return jdbc.sql("select " + ENTRY_COLS + " from entry where id = :id")
        .param("id", id)
        .query(EntryRepository::entry)
        .optional();
  }

  /** Verrouille la ligne pour la durée de la transaction (correction, archivage). */
  public Optional<Entry> findForUpdate(UUID id) {
    return jdbc.sql("select " + ENTRY_COLS + " from entry where id = :id for update")
        .param("id", id)
        .query(EntryRepository::entry)
        .optional();
  }

  public Optional<EntryVersion> currentVersion(UUID entryId) {
    return jdbc.sql(
            "select "
                + VERSION_COLS
                + " from entry_version v where v.entry_id = :id and v.version ="
                + " (select current_version from entry where id = :id)")
        .param("id", entryId)
        .query(EntryRepository::version)
        .optional();
  }

  public int versionCount(UUID entryId) {
    return jdbc.sql("select count(*) from entry_version where entry_id = :id")
        .param("id", entryId)
        .query(Integer.class)
        .single();
  }

  public Optional<FeelingDetail> feeling(UUID entryId) {
    return jdbc.sql(
            "select emotion_code, original_emotion_code, intensity, valence, arousal,"
                + " context_note, bodily_signs from entry_feeling where entry_id = :id")
        .param("id", entryId)
        .query(
            (rs, i) -> {
              java.sql.Array arr = rs.getArray("bodily_signs");
              List<String> signs = arr == null ? null : Arrays.asList((String[]) arr.getArray());
              return new FeelingDetail(
                  rs.getString("emotion_code"),
                  rs.getString("original_emotion_code"),
                  rs.getInt("intensity"),
                  nullableInt(rs, "valence"),
                  nullableInt(rs, "arousal"),
                  rs.getString("context_note"),
                  signs);
            })
        .optional();
  }

  public Optional<MedicationDetail> medication(UUID entryId) {
    return jdbc.sql(
            "select molecule, dosage, frequency, prescriber, period_from, period_to"
                + " from entry_medication where entry_id = :id")
        .param("id", entryId)
        .query(
            (rs, i) ->
                new MedicationDetail(
                    rs.getString("molecule"),
                    rs.getString("dosage"),
                    rs.getString("frequency"),
                    rs.getString("prescriber"),
                    rs.getObject("period_from", LocalDate.class),
                    rs.getObject("period_to", LocalDate.class)))
        .optional();
  }

  public Optional<SessionDetail> session(UUID entryId) {
    return jdbc.sql(
            "select kind, modality, duration_min, closed_at from entry_session"
                + " where entry_id = :id")
        .param("id", entryId)
        .query(
            (rs, i) ->
                new SessionDetail(
                    rs.getString("kind"),
                    rs.getString("modality"),
                    nullableInt(rs, "duration_min"),
                    instant(rs, "closed_at")))
        .optional();
  }

  public Optional<EntryType> typeOf(UUID entryId) {
    return jdbc.sql("select type from entry where id = :id")
        .param("id", entryId)
        .query(String.class)
        .optional()
        .flatMap(EntryType::fromCode);
  }

  /** Patient de l'entrée, vide si elle n'existe pas. */
  public Optional<UUID> patientOf(UUID entryId) {
    return jdbc.sql("select patient_id from entry where id = :id")
        .param("id", entryId)
        .query(UUID.class)
        .optional();
  }

  /** Vrai si l'entrée est une séance (type session) de ce patient. */
  public boolean isSessionOfPatient(UUID sessionId, UUID patientId) {
    return jdbc.sql(
                "select count(*) from entry where id = :s and patient_id = :p"
                    + " and type = 'session' and archived_at is null")
            .param("s", sessionId)
            .param("p", patientId)
            .query(Integer.class)
            .single()
        > 0;
  }

  /** Une séance d'accueil existe déjà pour ce patient. */
  public boolean intakeExists(UUID patientId) {
    return jdbc.sql(
                "select count(*) from entry e join entry_session s on s.entry_id = e.id"
                    + " where e.patient_id = :p and s.kind = 'intake'")
            .param("p", patientId)
            .query(Integer.class)
            .single()
        > 0;
  }

  /** Verrou de transaction : sérialise la création de la séance d'accueil d'un patient. */
  public void lockIntake(UUID patientId) {
    jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:k, 0))")
        .param("k", "intake:" + patientId)
        .query()
        .listOfRows();
  }

  private static final String SESSION_VIEW_SELECT =
      "select e.id, e.patient_id, s.kind, s.modality, e.occurred_from, s.closed_at, s.duration_min"
          + " from entry e join entry_session s on s.entry_id = e.id"
          + " where e.patient_id = :p and e.type = 'session' and e.archived_at is null";

  private static SessionView sessionView(ResultSet rs, int i) throws SQLException {
    return new SessionView(
        uuid(rs, "id"),
        uuid(rs, "patient_id"),
        rs.getString("kind"),
        rs.getString("modality"),
        instant(rs, "occurred_from"),
        instant(rs, "closed_at"),
        nullableInt(rs, "duration_min"));
  }

  /**
   * Séance ouverte du patient : non archivée, closed_at nul, hors accueil (l'accueil, ouvert par
   * construction, n'est jamais « la séance ouverte »). La plus récente si plusieurs.
   */
  public Optional<SessionView> openSession(UUID patientId) {
    return jdbc.sql(
            SESSION_VIEW_SELECT
                + " and s.closed_at is null and s.kind <> 'intake'"
                + " order by e.occurred_from desc limit 1")
        .param("p", patientId)
        .query(EntryRepository::sessionView)
        .optional();
  }

  /**
   * Séance courante : l'ouverte, sinon la dernière clôturée (séance ou contact, par occurred_from
   * puis closed_at), sinon l'accueil. Les séances archivées sont ignorées.
   */
  public Optional<SessionView> currentSession(UUID patientId) {
    return jdbc.sql(
            SESSION_VIEW_SELECT
                + " order by case when s.closed_at is null and s.kind <> 'intake' then 0"
                + " when s.kind <> 'intake' then 1 else 2 end,"
                + " e.occurred_from desc, s.closed_at desc nulls last limit 1")
        .param("p", patientId)
        .query(EntryRepository::sessionView)
        .optional();
  }

  /** Verrou de transaction : sérialise le démarrage des séances d'un patient. */
  public void lockSessions(UUID patientId) {
    jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:k, 0))")
        .param("k", "session:" + patientId)
        .query()
        .listOfRows();
  }

  // ---- écriture

  /** Désigne la version qui tient lieu de compte rendu de la séance (clôture, contact). */
  public void setSummaryVersion(UUID entryId, UUID versionId) {
    jdbc.sql("update entry_session set summary_version_id = :v where entry_id = :id")
        .param("id", entryId)
        .param("v", versionId)
        .update();
  }

  /**
   * Insère l'entrée. author_type et submission_id ne sont pas écrits : les défauts de la colonne
   * ('practitioner', NULL) s'appliquent toujours en V1 (A11).
   */
  public void insertEntry(
      UUID id,
      UUID tenantId,
      UUID patientId,
      EntryType type,
      EntryStatus status,
      Visibility visibility,
      TimeSpan time,
      UUID sessionId,
      UUID currentVersionId,
      UUID createdBy,
      Instant now) {
    jdbc.sql(
            "insert into entry (id, tenant_id, patient_id, type, status, visibility,"
                + " occurred_from, occurred_to, occurred_precision, occurred_label, recorded_at,"
                + " session_id, current_version_id, current_version, created_by, created_at,"
                + " updated_at) values (:id, :tenant, :patient, :type, :status, :visibility,"
                + " :from, :to, :precision, :label, :now, :session, :version, 1, :by, :now, :now)")
        .param("id", id)
        .param("tenant", tenantId)
        .param("patient", patientId)
        .param("type", type.code())
        .param("status", status.code())
        .param("visibility", visibility.code())
        .param("from", odt(time.from()))
        .param("to", odt(time.to()))
        .param("precision", time.precision().code())
        .param("label", time.label())
        .param("now", odt(now))
        .param("session", sessionId)
        .param("version", currentVersionId)
        .param("by", createdBy)
        .update();
  }

  public void insertVersion(
      UUID id,
      UUID entryId,
      int version,
      String title,
      String body,
      String payloadJson,
      String reason,
      UUID authorId,
      Instant now) {
    jdbc.sql(
            "insert into entry_version (id, entry_id, version, title, body, payload, reason,"
                + " author_id, created_at) values (:id, :entry, :version, :title, :body,"
                + " cast(:payload as jsonb), :reason, :author, :now)")
        .param("id", id)
        .param("entry", entryId)
        .param("version", version)
        .param("title", title)
        .param("body", body)
        .param("payload", payloadJson)
        .param("reason", reason)
        .param("author", authorId)
        .param("now", odt(now))
        .update();
  }

  /** Correction avec version : l'entrée pointe vers la nouvelle version. */
  public void updateEntryContent(
      UUID id,
      EntryStatus status,
      Visibility visibility,
      TimeSpan time,
      UUID currentVersionId,
      int currentVersion,
      Instant now) {
    jdbc.sql(
            "update entry set status = :status, visibility = :visibility, occurred_from = :from,"
                + " occurred_to = :to, occurred_precision = :precision, occurred_label = :label,"
                + " current_version_id = :version, current_version = :n, updated_at = :now"
                + " where id = :id")
        .param("id", id)
        .param("status", status.code())
        .param("visibility", visibility.code())
        .param("from", odt(time.from()))
        .param("to", odt(time.to()))
        .param("precision", time.precision().code())
        .param("label", time.label())
        .param("version", currentVersionId)
        .param("n", currentVersion)
        .param("now", odt(now))
        .update();
  }

  /** Modification en place (règle de grâce) : la version et ses pointeurs ne bougent pas. */
  public void updateEntryInPlace(
      UUID id, EntryStatus status, Visibility visibility, TimeSpan time, Instant now) {
    jdbc.sql(
            "update entry set status = :status, visibility = :visibility, occurred_from = :from,"
                + " occurred_to = :to, occurred_precision = :precision, occurred_label = :label,"
                + " updated_at = :now where id = :id")
        .param("id", id)
        .param("status", status.code())
        .param("visibility", visibility.code())
        .param("from", odt(time.from()))
        .param("to", odt(time.to()))
        .param("precision", time.precision().code())
        .param("label", time.label())
        .param("now", odt(now))
        .update();
  }

  /** Règle de grâce uniquement : réécrit la version courante (title, body, payload). */
  public void updateVersionInPlace(UUID versionId, String title, String body, String payloadJson) {
    jdbc.sql(
            "update entry_version set title = :title, body = :body,"
                + " payload = cast(:payload as jsonb) where id = :id")
        .param("id", versionId)
        .param("title", title)
        .param("body", body)
        .param("payload", payloadJson)
        .update();
  }

  public void archive(UUID id, Instant now) {
    jdbc.sql("update entry set archived_at = :now, updated_at = :now where id = :id")
        .param("id", id)
        .param("now", odt(now))
        .update();
  }

  public void insertFeeling(UUID entryId, FeelingDetail f) {
    jdbc.sql(
            "insert into entry_feeling (entry_id, emotion_code, original_emotion_code, intensity,"
                + " valence, arousal, context_note, bodily_signs) values (:id, :emotion, :original,"
                + " :intensity, :valence, :arousal, :note, :signs)")
        .param("id", entryId)
        .param("emotion", f.emotionCode())
        .param("original", f.originalEmotionCode())
        .param("intensity", f.intensity())
        .param("valence", f.valence())
        .param("arousal", f.arousal())
        .param("note", f.contextNote())
        .param("signs", signs(f))
        .update();
  }

  public void updateFeeling(UUID entryId, FeelingDetail f) {
    jdbc.sql(
            "update entry_feeling set emotion_code = :emotion, original_emotion_code = :original,"
                + " intensity = :intensity, valence = :valence, arousal = :arousal,"
                + " context_note = :note, bodily_signs = :signs where entry_id = :id")
        .param("id", entryId)
        .param("emotion", f.emotionCode())
        .param("original", f.originalEmotionCode())
        .param("intensity", f.intensity())
        .param("valence", f.valence())
        .param("arousal", f.arousal())
        .param("note", f.contextNote())
        .param("signs", signs(f))
        .update();
  }

  private static String[] signs(FeelingDetail f) {
    return f.bodilySigns() == null ? null : f.bodilySigns().toArray(new String[0]);
  }

  public void insertMedication(UUID entryId, MedicationDetail m) {
    jdbc.sql(
            "insert into entry_medication (entry_id, molecule, dosage, frequency, prescriber,"
                + " period_from, period_to) values (:id, :molecule, :dosage, :frequency,"
                + " :prescriber, :from, :to)")
        .param("id", entryId)
        .param("molecule", m.molecule())
        .param("dosage", m.dosage())
        .param("frequency", m.frequency())
        .param("prescriber", m.prescriber())
        .param("from", m.periodFrom())
        .param("to", m.periodTo())
        .update();
  }

  public void updateMedication(UUID entryId, MedicationDetail m) {
    jdbc.sql(
            "update entry_medication set molecule = :molecule, dosage = :dosage,"
                + " frequency = :frequency, prescriber = :prescriber, period_from = :from,"
                + " period_to = :to where entry_id = :id")
        .param("id", entryId)
        .param("molecule", m.molecule())
        .param("dosage", m.dosage())
        .param("frequency", m.frequency())
        .param("prescriber", m.prescriber())
        .param("from", m.periodFrom())
        .param("to", m.periodTo())
        .update();
  }

  public void insertSession(UUID entryId, SessionDetail s) {
    jdbc.sql(
            "insert into entry_session (entry_id, kind, modality, duration_min, closed_at)"
                + " values (:id, :kind, :modality, :duration, :closed)")
        .param("id", entryId)
        .param("kind", s.kindOrDefault())
        .param("modality", s.modality())
        .param("duration", s.durationMin())
        .param("closed", odt(s.closedAt()))
        .update();
  }

  public void updateSession(UUID entryId, SessionDetail s) {
    jdbc.sql(
            "update entry_session set kind = :kind, modality = :modality,"
                + " duration_min = :duration, closed_at = :closed where entry_id = :id")
        .param("id", entryId)
        .param("kind", s.kindOrDefault())
        .param("modality", s.modality())
        .param("duration", s.durationMin())
        .param("closed", odt(s.closedAt()))
        .update();
  }
}
