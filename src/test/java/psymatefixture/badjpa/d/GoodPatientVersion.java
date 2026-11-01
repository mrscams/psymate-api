package psymatefixture.badjpa.d;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Correct : table append-only avec @Immutable (nom de table en majuscules : insensible a la casse).
 */
@Entity
@Immutable
@Table(name = "PATIENT_VERSION")
public class GoodPatientVersion {
  @Id UUID id;
}
