package psymatefixture.badroles.shop.service;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Violation : une entite hors de model/. */
@Entity
public class StrayEntity {
  @Id UUID id;
}
