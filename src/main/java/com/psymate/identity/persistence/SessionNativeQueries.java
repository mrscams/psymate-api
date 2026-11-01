package com.psymate.identity.persistence;

import java.util.UUID;

/**
 * Révocation des sessions serveur. {@code spring_session} appartient à Spring Session : aucune
 * entité, trois DELETE ciblés ({@code principal_name} = id du praticien). Chaque méthode renvoie le
 * nombre de sessions supprimées.
 */
public interface SessionNativeQueries {

  /** Révoque toutes les sessions du praticien. */
  int deleteSessions(UUID practitionerId);

  /** Révoque les autres sessions du praticien (toutes sauf {@code currentSessionId}). */
  int deleteOtherSessions(UUID practitionerId, String currentSessionId);
}
