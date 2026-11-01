package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminCursors.Position;
import com.psymate.identity.persistence.Rows;
import com.psymate.identity.persistence.SecurityEventJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecture seule de la vue admin_journal_v (E3.13, G Q29) jointe à practitioner pour les noms, par
 * le fragment natif {@code AdminJournalNativeQueries} (aucune entité sur la vue). La vue ne porte
 * ni adresse IP ni user_agent ; cette classe ne les lit jamais. Les lignes d'audit patient (kind
 * patient.*) sont écartées : le journal d'administration n'expose aucune donnée patient, même un
 * identifiant opaque.
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

  private final SecurityEventJpaRepository events;

  AdminJournalRepository(SecurityEventJpaRepository events) {
    this.events = events;
  }

  /**
   * Page triée par date décroissante puis identifiant décroissant, en une seule requête. Le type
   * s'apparie en égalité ou en préfixe (kind=admin donne admin et admin.*) ; le service garantit
   * que kind ne contient ni % ni _.
   */
  @Transactional(readOnly = true)
  List<JournalRow> page(Filter f, Position cursor, int limit) {
    return events
        .adminJournal(
            f.kind(),
            f.practitionerId(),
            f.actorId(),
            f.from(),
            f.to(),
            cursor == null ? null : cursor.key(),
            cursor == null ? null : cursor.id(),
            limit)
        .stream()
        .map(
            t ->
                new JournalRow(
                    Rows.uuid(t, "id"),
                    Rows.instant(t, "occurred_at"),
                    Rows.string(t, "kind"),
                    Rows.string(t, "source"),
                    Rows.uuid(t, "actor_id"),
                    Rows.string(t, "actor_name"),
                    Rows.uuid(t, "practitioner_id"),
                    Rows.string(t, "practitioner_name"),
                    Rows.string(t, "summary"),
                    Rows.string(t, "details")))
        .toList();
  }
}
