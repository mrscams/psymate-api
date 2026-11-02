package psymatefixture.badtables.alpha;

import org.springframework.data.jpa.repository.Query;

/** Violation : la chaine d'un @Query est lue (lecture d'une table de beta par alpha). */
public interface AlphaQueryRepository {
  @Query("select b from BetaItem b")
  java.util.List<Object> all();
}
