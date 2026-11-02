package com.psymate.clinical.export.repository;

import com.psymate.clinical.model.TimeSpan;
import jakarta.persistence.Tuple;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Lecture du dossier à exporter, en records. Le SQL, et avec lui les filtres de confidentialité
 * ({@code visibility = 'clinical'}, {@code archived_at is null}, version courante seulement), est
 * dans le fragment natif {@link ExportNativeQueries} (G Q38) ; cette classe ne fait que lire ses
 * colonnes.
 */
@Repository
public class ExportRepository {

  /** Séance exportable : détail entry_session et version courante (compte rendu). */
  public record SessionRow(
      UUID id,
      TimeSpan time,
      String kind,
      String modality,
      Integer durationMin,
      String title,
      String body) {}

  /** Entrée exportable hors séance, avec ses détails typés. */
  public record EntryRow(
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

  private final ExportJobJpaRepository queries;

  ExportRepository(ExportJobJpaRepository queries) {
    this.queries = queries;
  }

  private static UUID uuid(Tuple row, String alias) {
    return (UUID) row.get(alias);
  }

  private static String string(Tuple row, String alias) {
    return (String) row.get(alias);
  }

  private static Integer nullableInt(Tuple row, String alias) {
    Object value = row.get(alias);
    return value == null ? null : ((Number) value).intValue();
  }

  /**
   * Hibernate rend un {@code timestamptz} natif sous la forme que le pilote déclare ({@code
   * Timestamp}, {@code OffsetDateTime} ou {@code Instant}) : les trois sont acceptées.
   */
  private static Instant instant(Tuple row, String alias) {
    Object value = row.get(alias);
    return switch (value) {
      case null -> null;
      case Instant i -> i;
      case OffsetDateTime o -> o.toInstant();
      case java.util.Date d -> d.toInstant();
      default ->
          throw new IllegalStateException(
              "Type de date inattendu pour " + alias + " : " + value.getClass().getName());
    };
  }

  /** Un {@code date} natif arrive en {@code LocalDate} ou en {@code java.sql.Date}. */
  private static LocalDate localDate(Tuple row, String alias) {
    Object value = row.get(alias);
    return switch (value) {
      case null -> null;
      case LocalDate l -> l;
      case java.sql.Date d -> d.toLocalDate();
      default ->
          throw new IllegalStateException(
              "Type de date inattendu pour " + alias + " : " + value.getClass().getName());
    };
  }

  private static TimeSpan time(Tuple row) {
    return new TimeSpan(
        instant(row, "occurred_from"),
        instant(row, "occurred_to"),
        TimeSpan.Precision.fromCode(string(row, "occurred_precision")),
        string(row, "occurred_label"));
  }

  /** Séances non archivées et cliniques, de la plus ancienne à la plus récente. */
  public List<SessionRow> sessions(UUID patientId) {
    return queries.exportableSessions(patientId).stream()
        .map(
            row ->
                new SessionRow(
                    uuid(row, "id"),
                    time(row),
                    string(row, "kind"),
                    string(row, "modality"),
                    nullableInt(row, "duration_min"),
                    string(row, "title"),
                    string(row, "body")))
        .toList();
  }

  /** Entrées (hors séances) non archivées et cliniques, de la plus ancienne à la plus récente. */
  public List<EntryRow> entries(UUID patientId) {
    return queries.exportableEntries(patientId).stream()
        .map(
            row ->
                new EntryRow(
                    uuid(row, "id"),
                    uuid(row, "session_id"),
                    string(row, "type"),
                    "draft".equals(string(row, "status")),
                    time(row),
                    string(row, "title"),
                    string(row, "body"),
                    string(row, "emotion_code"),
                    nullableInt(row, "intensity"),
                    string(row, "molecule"),
                    string(row, "dosage"),
                    string(row, "frequency"),
                    localDate(row, "period_from"),
                    localDate(row, "period_to")))
        .toList();
  }

  /** Tags posés sur les entrées exportables du patient : entrée -> identifiants de tags. */
  public Map<UUID, List<UUID>> tagIds(UUID patientId) {
    Map<UUID, List<UUID>> byEntry = new LinkedHashMap<>();
    for (Tuple row : queries.exportableEntryTags(patientId)) {
      byEntry
          .computeIfAbsent(uuid(row, "entry_id"), k -> new ArrayList<>())
          .add(uuid(row, "tag_id"));
    }
    return byEntry;
  }
}
