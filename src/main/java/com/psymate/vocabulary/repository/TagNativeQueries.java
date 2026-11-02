package com.psymate.vocabulary.repository;

import java.util.UUID;

/**
 * Fusion de tags : compteur d'usage gardé en SQL natif (checklist §1) car il repose sur une
 * sous-requête que JPQL exprime mal ({@code usage_count + (select ...)}). Les tables où les tags
 * sont posés (entry_tag, patient_tag) appartiennent à clinical et patients, qui les réécrivent
 * eux-mêmes sur {@code TagsMerged} (M9-03).
 */
public interface TagNativeQueries {

  /**
   * Additionne atomiquement {@code usage_count} de la source à la cible, puis rafraîchit l'entité
   * cible du contexte de persistance (sinon elle porterait l'ancien compteur). L'archivage de la
   * source reste une méthode d'entité ({@code Tag.archiveInto}).
   */
  public void addUsageCount(UUID source, UUID target);
}
