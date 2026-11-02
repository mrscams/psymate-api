package com.psymate.vocabulary.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/**
 * Fusion d'émotions annulée dans les 30 jours (M9-03, revue ARCH-01) : les ressentis passés de
 * {@code sourceCode} à {@code targetCode} par la fusion {@code mergeId} ({@link EmotionsMerged})
 * reprennent la source. Publié dans la transaction de l'annulation ; chaque module propriétaire
 * réécrit ses propres tables.
 */
public class EmotionMergeReverted extends DomainEvent {

  private UUID mergeId;
  private UUID practitionerId;
  private String sourceCode;
  private String targetCode;

  /** Pour la désérialisation. */
  protected EmotionMergeReverted() {}

  public EmotionMergeReverted(
      UUID tenantId, UUID mergeId, UUID practitionerId, String sourceCode, String targetCode) {
    super(tenantId);
    this.mergeId = mergeId;
    this.practitionerId = practitionerId;
    this.sourceCode = sourceCode;
    this.targetCode = targetCode;
  }

  public UUID getMergeId() {
    return mergeId;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public String getSourceCode() {
    return sourceCode;
  }

  public String getTargetCode() {
    return targetCode;
  }
}
