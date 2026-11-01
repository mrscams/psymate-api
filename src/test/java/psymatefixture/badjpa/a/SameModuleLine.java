package psymatefixture.badjpa.a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.UUID;

/** Correct : association vers une entite du meme module. */
@Entity
public class SameModuleLine {
  @Id UUID id;

  @ManyToOne AOwner owner;

  /** Correct aussi : identifiant transverse nu. */
  UUID otherModuleId;
}
