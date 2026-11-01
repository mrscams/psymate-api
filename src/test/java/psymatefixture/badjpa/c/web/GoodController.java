package psymatefixture.badjpa.c.web;

import java.util.UUID;
import psymatefixture.badjpa.c.GoodDto;

/** Correct : une classe web qui ne manipule que des Dto. */
public class GoodController {
  public GoodDto get() {
    return new GoodDto(UUID.randomUUID());
  }
}
