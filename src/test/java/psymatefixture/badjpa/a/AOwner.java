package psymatefixture.badjpa.a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class AOwner {
  @Id UUID id;
}
