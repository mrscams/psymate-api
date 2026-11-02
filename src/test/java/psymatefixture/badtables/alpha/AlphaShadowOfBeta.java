package psymatefixture.badtables.alpha;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Violation : une entite d'alpha mappe une table de beta. */
@Entity
@Table(name = "beta_item")
public class AlphaShadowOfBeta {
  @Id UUID id;
}
