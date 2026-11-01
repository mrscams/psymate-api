package psymatefixture.badjpa.a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import java.util.Set;
import java.util.UUID;
import psymatefixture.badjpa.b.BEntity;

/** Violation : collection ManyToMany d'entites d'un autre module, portee par un champ. */
@Entity
public class CrossModuleManyToMany {
  @Id UUID id;

  @ManyToMany Set<BEntity> targets;
}
