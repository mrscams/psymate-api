package psymatefixture.badtables.beta;

import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface BetaItemRepository extends CrudRepository<BetaItem, UUID> {
  /** Suppression derivee par Spring Data : vise beta_item. */
  void deleteByName(String name);

  /** Requete propre : son SQL (beta_link, supprimable) est lu par le scan des chaines. */
  @Modifying
  @Query("delete from BetaLink l where l.id = ?1")
  int deleteLinkOf(UUID id);
}
