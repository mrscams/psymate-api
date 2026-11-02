package com.psymate.vocabulary.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/**
 * Émotion personnalisée fusionnée dans une autre (M9-03, revue ARCH-01) : les ressentis du
 * praticien qui portaient {@code sourceCode} doivent porter {@code targetCode}, en gardant la
 * source comme émotion d'origine. Publié dans la transaction de la fusion ; chaque module
 * propriétaire réécrit ses propres tables. {@code mergeId} identifie la fusion, que {@link
 * EmotionMergeReverted} peut annuler ensuite.
 */
public class EmotionsMerged extends DomainEvent {

  private UUID mergeId;
  private UUID practitionerId;
  private String sourceCode;
  private String targetCode;

  /** Pour la désérialisation. */
  protected EmotionsMerged() {}

  public EmotionsMerged(
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
