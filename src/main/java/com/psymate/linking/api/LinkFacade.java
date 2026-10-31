package com.psymate.linking.api;

import java.util.List;
import java.util.UUID;

/**
 * Lecture des liens pour les autres modules. Les liens {@code superseded} et {@code rejected} n'y
 * figurent jamais (A7). Ces méthodes ne révèlent pas l'existence d'une entrée d'un autre praticien
 * (ADR-09) : liste vide et compte nul.
 */
public interface LinkFacade {

  /** Liens acceptés dont l'entrée est la source ou la cible, du plus ancien au plus récent. */
  List<LinkView> acceptedLinksOf(UUID entryId);

  /** Nombre de liens acceptés de l'entrée (source ou cible). */
  int countFor(UUID entryId);
}
