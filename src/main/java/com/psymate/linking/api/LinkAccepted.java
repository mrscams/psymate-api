package com.psymate.linking.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/**
 * Lien accepté (événement de domaine, persisté dans le registre d'événements). En V1 il est publié
 * à la création manuelle d'un lien et au changement de type (le nouveau lien) ; en V2 il le sera
 * aussi à l'acceptation d'une suggestion de l'IA.
 */
public class LinkAccepted extends DomainEvent {

  private UUID linkId;
  private UUID patientId;
  private UUID sourceId;
  private UUID targetId;
  private String linkType;
  private String provenance;
  private UUID supersededLinkId;

  /** Pour la désérialisation. */
  protected LinkAccepted() {}

  /**
   * @param supersededLinkId lien remplacé par celui-ci (changement de type), nul sinon
   */
  public LinkAccepted(
      UUID tenantId,
      UUID linkId,
      UUID patientId,
      UUID sourceId,
      UUID targetId,
      String linkType,
      String provenance,
      UUID supersededLinkId) {
    super(tenantId);
    this.linkId = linkId;
    this.patientId = patientId;
    this.sourceId = sourceId;
    this.targetId = targetId;
    this.linkType = linkType;
    this.provenance = provenance;
    this.supersededLinkId = supersededLinkId;
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

  /** manual, ou ai_confirmed (V2). */
  public String getProvenance() {
    return provenance;
  }

  public UUID getSupersededLinkId() {
    return supersededLinkId;
  }
}
