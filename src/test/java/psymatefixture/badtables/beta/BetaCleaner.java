package psymatefixture.badtables.beta;

import java.util.UUID;

/**
 * Violation : suppressions physiques par Spring Data d'une table hors E9 (heritee et derivee) ;
 * correct pour beta_link et pour une methode {@code @Query}.
 */
public class BetaCleaner {
  BetaItemRepository items;
  BetaLinkRepository links;

  void clean(UUID id) {
    items.deleteById(id);
    items.deleteByName("x");
    items.deleteLinkOf(id);
    links.deleteAll();
  }
}
