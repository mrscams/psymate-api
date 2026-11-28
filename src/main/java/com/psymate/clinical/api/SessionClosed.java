package com.psymate.clinical.api;

import com.psymate.events.api.DomainEvent;
import com.psymate.shared.Reserved;
import java.time.Instant;
import java.util.UUID;

/**
 * Séance close (G Q85, Q86) : publiée une fois par clôture réussie, dans la transaction de clôture,
 * quel que soit le kind (séance, contact ; un contact naît clos et la publie à sa création). Ne
 * porte aucun texte clinique : ni titre, ni compte rendu. S'ajoute à {@link EntryCorrected},
 * toujours publiée par la clôture.
 */
@Reserved(
    consumer = "M15-07, G Q86 : module billing (séances à facturer)",
    reason = "G Q85 : publiée dès M15-02, avant son premier abonné")
public class SessionClosed extends DomainEvent {

  private UUID sessionId;
  private UUID patientId;
  private UUID practitionerId;
  private String kind;
  private String modality;
  private Instant occurredFrom;
  private Instant closedAt;
  private Integer durationMin;

  /** Pour la désérialisation. */
  protected SessionClosed() {}

  public SessionClosed(
      UUID tenantId,
      UUID sessionId,
      UUID patientId,
      UUID practitionerId,
      String kind,
      String modality,
      Instant occurredFrom,
      Instant closedAt,
      Integer durationMin) {
    super(tenantId);
    this.sessionId = sessionId;
    this.patientId = patientId;
    this.practitionerId = practitionerId;
    this.kind = kind;
    this.modality = modality;
    this.occurredFrom = occurredFrom;
    this.closedAt = closedAt;
    this.durationMin = durationMin;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  /**
   * Praticien propriétaire du patient, qui seul peut clore la séance ; même valeur que {@link
   * SessionView#practitionerId()} (en V1 le propriétaire d'un patient ne change pas).
   */
  public UUID getPractitionerId() {
    return practitionerId;
  }

  /** session, contact ou intake. */
  public String getKind() {
    return kind;
  }

  /** in_person, video, phone ou email. */
  public String getModality() {
    return modality;
  }

  /** Début de la séance. */
  public Instant getOccurredFrom() {
    return occurredFrom;
  }

  /** Instant de clôture retenu (horloge du serveur). */
  public Instant getClosedAt() {
    return closedAt;
  }

  /** Durée retenue en minutes ; nulle pour un contact consigné sans durée. */
  public Integer getDurationMin() {
    return durationMin;
  }
}
