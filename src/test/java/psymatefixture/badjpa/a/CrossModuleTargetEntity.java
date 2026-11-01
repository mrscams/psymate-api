package psymatefixture.badjpa.a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.List;
import java.util.UUID;
import psymatefixture.badjpa.b.BEntity;

/** Violation : cible d'un autre module declaree par targetEntity, type de champ neutre. */
@Entity
public class CrossModuleTargetEntity {
  @Id UUID id;

  @OneToMany(targetEntity = BEntity.class)
  List<Object> targets;
}
