package psymatefixture.badtables.beta;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Correct : beta mappe sa propre table. */
@Entity
@Table(name = "beta_item")
public class BetaItem {
  @Id UUID id;
}
