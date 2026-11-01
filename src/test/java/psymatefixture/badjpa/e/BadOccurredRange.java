package psymatefixture.badjpa.e;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Violation : champ occurredRange (colonne generee) sans @Column. */
@Entity
public class BadOccurredRange {
  @Id UUID id;

  String occurredRange;
}
