package psymatefixture.badjpa.e;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Violation : colonne ecrite par trigger, mais modifiable. */
@Entity
public class BadSearchVector {
  @Id UUID id;

  @Column(name = "search_vector")
  String vector;
}
