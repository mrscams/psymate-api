package com.psymate.vocabulary.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * État des fusions d'un praticien, pour les abonnés de {@link EmotionsMerged}, {@link
 * EmotionMergeReverted} et {@link TagsMerged} (M9-03). Un abonné ne rejoue pas l'événement : il
 * relit ici l'état voulu et réécrit ses propres tables en conséquence. L'ordre d'arrivée des
 * événements (fusion puis annulation, fusions en chaîne) ne compte donc pas, et un rejeu ne change
 * rien. Le praticien est passé en paramètre : un abonné n'a pas de session.
 */
public interface MergeFacade {

  /**
   * Résolution de chaque émotion personnalisée du praticien déjà source d'une fusion dont
   * l'historique ({@link EmotionResolution#historicalCodes()}) contient {@code code} : celles dont
   * les ressentis peuvent changer quand une fusion touchant {@code code} est posée ou annulée.
   * Liste triée par code source ; vide si aucune.
   */
  List<EmotionResolution> emotionResolutions(UUID practitionerId, String code);

  /**
   * Pour chaque tag du praticien archivé par une fusion, le tag actif qui le remplace (la chaîne
   * {@code merged_into} suivie jusqu'au bout) ; vide si aucun.
   */
  Map<UUID, UUID> tagResolutions(UUID practitionerId);
}
