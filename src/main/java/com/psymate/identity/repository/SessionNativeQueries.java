package com.psymate.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Révocation des sessions serveur. {@code spring_session} appartient à Spring Session : aucune
 * entité, des DELETE ciblés ({@code principal_name} = id du praticien) et une lecture des sessions
 * actives (M10-06). Chaque suppression renvoie le nombre de sessions supprimées.
 */
public interface SessionNativeQueries {

  /** Révoque toutes les sessions du praticien. */
  int deleteSessions(UUID practitionerId);

  /** Révoque les autres sessions du praticien (toutes sauf {@code currentSessionId}). */
  int deleteOtherSessions(UUID practitionerId, String currentSessionId);

  /**
   * Session active d'un praticien, lue dans {@code spring_session}. {@code primaryId} est la clé
   * interne de la ligne (stable, jamais envoyée au navigateur) ; {@code sessionId} est
   * l'identifiant HTTP de la session (celui que porte le cookie, encodé) : il ne sort jamais de
   * l'API et ne sert qu'à reconnaître la session courante. Les horodatages viennent des colonnes en
   * millisecondes. {@code device} et {@code openedAt} sont les attributs PsyMate de la session
   * ({@link com.psymate.identity.model.SessionAttributes}), null s'ils n'ont pas été posés (session
   * ouverte avant M10-06).
   */
  record ActiveSessionRow(
      String primaryId,
      String sessionId,
      Instant createdAt,
      Instant lastAccessAt,
      Instant expiresAt,
      String device,
      Instant openedAt) {}

  /**
   * Sessions du praticien non expirées à {@code now}, les plus récemment utilisées d'abord. Les
   * lignes expirées que le nettoyage de Spring Session n'a pas encore supprimées sont ignorées.
   */
  List<ActiveSessionRow> findActiveSessions(UUID practitionerId, Instant now);

  /**
   * Révoque une seule session du praticien, désignée par sa clé interne ; 0 si elle n'existe pas.
   */
  int deleteSessionByPrimaryId(UUID practitionerId, String primaryId);
}
