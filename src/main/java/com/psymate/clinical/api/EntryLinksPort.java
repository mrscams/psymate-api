package com.psymate.clinical.api;

import java.util.List;
import java.util.UUID;

/**
 * Port de création des liens d'une entrée neuve (M2-02). Défini par clinical, implémenté par
 * linking : clinical n'a ainsi aucune dépendance vers linking (A3) et la création de l'entrée et de
 * ses liens se fait dans la transaction de l'appelant. L'implémentation REJOINT la transaction en
 * cours ; toute exception annule donc l'entrée avec ses liens (tout ou rien).
 */
public interface EntryLinksPort {

  /**
   * Pose un lien manuel accepté par élément de {@code links}, dans l'ordre, pour l'entrée {@code
   * entryId} venant d'être écrite.
   *
   * @return les liens posés, dans l'ordre de la demande
   * @throws EntryLinkException le premier lien refusé par les règles métier (422 côté REST)
   */
  List<CreatedLink> createForNewEntry(UUID entryId, List<LinkRequest> links);
}
