package psymatefixture.badjpa.b;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class BEntity {
  @Id UUID id;
}
