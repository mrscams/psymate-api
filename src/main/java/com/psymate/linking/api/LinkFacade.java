package com.psymate.linking.api;

import com.psymate.clinical.api.CreatedLink;
import com.psymate.clinical.api.LinkRequest;
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

  /**
   * Pose les liens d'une entrée qui vient d'être créée (M2-02), dans la transaction de l'appelant :
   * un lien refusé lève {@link com.psymate.clinical.api.EntryLinkException} et annule tout. C'est
   * la seule méthode d'écriture de cette façade ; clinical y accède par le port {@link
   * com.psymate.clinical.api.EntryLinksPort}, que linking implémente.
   */
  List<CreatedLink> createForNewEntry(UUID entryId, List<LinkRequest> links);
}
