package psymatefixture.badjpa.c.flat;

import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controleur range hors de tout package web (module plat) : l'annotation suffit a le verifier. Il
 * ne touche aucune entite lui-meme, mais ses signatures en exposent par leurs types.
 */
@RestController
public class FlatLeakyController {
  /** Violation : LeakyLine, atteint par un champ de ListingView, porte une entite. */
  public ListingView list() {
    return new ListingView(java.util.List.of());
  }

  /** Violation : record imbrique d'un conteneur *Dtos, parametre d'entree. */
  public void create(LeakyDtos.CreateThing request) {}

  /** Correct : une vue qui ne porte que des valeurs. */
  public CleanView get() {
    return new CleanView(UUID.randomUUID());
  }

  /** Non verifie : une methode privee ne fait pas partie du contrat HTTP. */
  private UnexposedHelper helper() {
    return null;
  }
}
