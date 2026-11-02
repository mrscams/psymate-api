package psymatefixture.badroles.shop.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Correct : une entite va dans model/ meme si son nom finit par Job (comme ExportJob). */
@Entity
public class ShopJob {
  @Id UUID id;
}
