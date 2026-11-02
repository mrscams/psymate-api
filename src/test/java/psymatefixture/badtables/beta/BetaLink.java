package psymatefixture.badtables.beta;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Correct : table d'association, supprimable (liste E9 de la fixture). */
@Entity
@Table(name = "beta_link")
public class BetaLink {
  @Id UUID id;
}
