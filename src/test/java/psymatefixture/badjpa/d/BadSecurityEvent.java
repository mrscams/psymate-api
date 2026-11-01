package psymatefixture.badjpa.d;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Violation : table append-only sans @Immutable. */
@Entity
@Table(name = "security_event")
public class BadSecurityEvent {
  @Id UUID id;
}
