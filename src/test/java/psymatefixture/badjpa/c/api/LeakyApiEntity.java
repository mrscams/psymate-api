package psymatefixture.badjpa.c.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Violation : une entite dans le package api. */
@Entity
public class LeakyApiEntity {
  @Id UUID id;
}
