package com.psymate.linking.api;

import com.psymate.events.api.DomainEvent;
import com.psymate.shared.Reserved;
import java.util.UUID;

/**
 * Suggestion de lien refusée par le praticien (statut {@code rejected}, A7 « Cycle de vie d'un lien
 * »).
 *
 * <p>Jamais publié en V1 : le statut {@code rejected} est réservé aux suggestions de l'IA (V2,
 * {@code proposed} vers {@code rejected}). La suppression d'un lien posé à la main passe le lien en
 * {@code superseded} et n'émet aucun événement. La classe existe dès la V1 pour que l'outbox et les
 * abonnés futurs (A4.9, M7-05) disposent du type d'événement.
 */
@Reserved(
    consumer = "V2, suggestions de l'IA (module ai)",
    reason = "Type d'événement prêt pour l'outbox ; jamais publié en V1")
public class LinkRejected extends DomainEvent {

  private UUID linkId;
  private UUID patientId;
  private UUID sourceId;
  private UUID targetId;
  private String linkType;

  /** Pour la désérialisation. */
  protected LinkRejected() {}

  public LinkRejected(
      UUID tenantId, UUID linkId, UUID patientId, UUID sourceId, UUID targetId, String linkType) {
    super(tenantId);
    this.linkId = linkId;
    this.patientId = patientId;
    this.sourceId = sourceId;
    this.targetId = targetId;
    this.linkType = linkType;
  }

  public UUID getLinkId() {
    return linkId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public UUID getTargetId() {
    return targetId;
  }

  public String getLinkType() {
    return linkType;
  }
}
