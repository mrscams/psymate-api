package psymatefixture.badjpa.d;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Violation : sans @Table, le nom de classe donne document_access_log (snake_case). */
@Entity
public class DocumentAccessLog {
  @Id UUID id;
}
