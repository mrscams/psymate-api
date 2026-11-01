package psymatefixture.badjpa.a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import psymatefixture.badjpa.b.BEntity;

/** Violation : association JPA vers une entite d'un autre module. */
@Entity
public class CrossModuleOrder {
  @Id UUID id;

  @ManyToOne BEntity target;
}
