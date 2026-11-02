package psymatefixture.badjpa.c.flat;

import psymatefixture.badjpa.c.CEntity;

/** Conteneur a l'ancienne (avant G Q35) : son record imbrique porte une entite. */
public final class LeakyDtos {
  private LeakyDtos() {}

  /** Violation : parametre d'entree d'un controleur. */
  public record CreateThing(CEntity entity) {}
}
