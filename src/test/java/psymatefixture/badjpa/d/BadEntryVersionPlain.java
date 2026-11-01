package psymatefixture.badjpa.d;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Violation : entry_version sans @Immutable ni @DynamicUpdate. */
@Entity
@Table(name = "entry_version")
public class BadEntryVersionPlain {
  @Id UUID id;
}
