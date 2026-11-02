package com.psymate.identity.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Une ligne du journal d'audit applicatif : un accès d'un praticien à une ressource patient. {@code
 * details} est stocké tel quel dans la colonne jsonb {@code security_event.details}.
 */
public record AuditEvent(
    UUID practitionerId,
    String kind,
    String ip,
    String userAgent,
    Map<String, Object> details,
    Instant occurredAt) {

  /** Lecture d'une ressource patient (GET, HEAD, OPTIONS). */
  public static final String PATIENT_READ = "patient.read";

  /** Modification d'une ressource patient (toute autre méthode). */
  public static final String PATIENT_WRITE = "patient.write";
}
