package psymatefixture.badjpa.e;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Correct : colonnes trigger / generees en lecture seule. */
@Entity
public class GoodGeneratedColumns {
  @Id UUID id;

  @Column(name = "search_vector", insertable = false, updatable = false)
  String vector;

  @Column(name = "occurred_range", insertable = false, updatable = false)
  String range;
}
