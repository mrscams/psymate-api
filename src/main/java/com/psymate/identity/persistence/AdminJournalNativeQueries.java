package com.psymate.identity.persistence;

import jakarta.persistence.Tuple;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lecture native de la vue {@code admin_journal_v} (E3.13, G Q29) jointe à practitioner pour les
 * noms. Aucune entité sur la vue. La vue ne porte ni adresse IP ni user_agent ; ce fragment ne les
 * lit jamais. Les lignes d'audit patient (kind {@code patient.*}) sont écartées : le journal
 * d'administration n'expose aucune donnée patient, même un identifiant opaque.
 */
public interface AdminJournalNativeQueries {

  /**
   * Page triée par date décroissante puis identifiant décroissant (colonnes : id, occurred_at,
   * kind, source, actor_id, actor_name, practitioner_id, practitioner_name, summary, details). Le
   * type s'apparie en égalité ou en préfixe (kind=admin donne admin et admin.*) ; l'appelant
   * garantit que kind ne contient ni % ni _. Un critère null est ignoré.
   *
   * @param cursorKey clé de tri du curseur en millisecondes, null pour la première page
   */
  List<Tuple> adminJournal(
      String kind,
      UUID practitionerId,
      UUID actorId,
      Instant from,
      Instant to,
      Long cursorKey,
      UUID cursorId,
      int limit);
}
