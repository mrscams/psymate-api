package psymatefixture.badjpa.a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.List;
import java.util.UUID;
import psymatefixture.badjpa.b.BEntity;

/** Violation : collection d'entites d'un autre module, portee par un getter. */
@Entity
public class CrossModuleCollection {
  @Id UUID id;

  @OneToMany
  List<BEntity> getTargets() {
    return List.of();
  }
}
