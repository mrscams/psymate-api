package psymatefixture.badjpa.c;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class CEntity {
  @Id UUID id;
}
