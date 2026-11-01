package com.psymate.clinical.api;

import java.util.List;
import java.util.UUID;

/**
 * Port de lecture des liens acceptés d'une entrée (export du dossier, M6-05). Défini par clinical,
 * implémenté par linking : clinical ne dépend pas de linking (A3, linking dépend déjà de clinical
 * :: api, un cycle serait refusé). Comme {@link EntryLinksPort}, il évite la dépendance inverse.
 *
 * <p>Aucun contrôle d'accès : l'appelant a déjà vérifié que l'entrée appartient au praticien qui
 * exporte (garde de propriété du patient, ou liste des patients du praticien). Il fonctionne donc
 * aussi hors requête web (travail d'export du compte), sans principal.
 */
public interface EntryLinksReadPort {

  /** Liens acceptés dont l'entrée est source ou cible, du plus ancien au plus récent. */
  List<LinkedEntryView> acceptedLinksOf(UUID entryId);
}
