package psymatefixture.badjpa.d;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

/** Correct : entry_version avec @DynamicUpdate seul. */
@Entity
@DynamicUpdate
@Table(name = "entry_version")
public class GoodEntryVersion {
  @Id UUID id;
}
