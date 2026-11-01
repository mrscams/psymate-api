package psymatefixture.badjpa.c.api;

import java.util.UUID;
import psymatefixture.badjpa.c.GoodDto;

/** Correct : la facade ne renvoie qu'un DTO. */
public class GoodFacade {
  public GoodDto load(UUID id) {
    return null;
  }
}
