package com.psymate.identity.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Écriture native des lignes d'audit patient (kind {@code patient.read} ou {@code patient.write}, G
 * Q29) dans {@code security_event}. Native, car l'entité {@link SecurityEvent} n'écrit jamais
 * {@code user_agent}, que l'audit renseigne.
 */
public interface AuditEventNativeQueries {

  /**
   * INSERT seul (table en ajout seul), dans sa propre transaction ; {@code actor_id} reste null.
   * {@code ip} est un littéral déjà normalisé ou null, {@code detailsJson} un objet JSON.
   */
  void insertAuditEvent(
      UUID id,
      UUID practitionerId,
      String kind,
      String ip,
      String userAgent,
      String detailsJson,
      OffsetDateTime occurredAt);
}
