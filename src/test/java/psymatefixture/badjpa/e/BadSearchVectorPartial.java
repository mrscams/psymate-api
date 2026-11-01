package psymatefixture.badjpa.e;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Violation : insertable = false ne suffit pas, updatable manque. */
@Entity
public class BadSearchVectorPartial {
  @Id UUID id;

  @Column(name = "search_vector", insertable = false)
  String vector;
}
