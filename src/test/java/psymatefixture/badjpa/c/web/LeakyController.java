package psymatefixture.badjpa.c.web;

import psymatefixture.badjpa.c.CEntity;

/** Violation : une classe web qui retourne une entite. */
public class LeakyController {
  public CEntity get() {
    return new CEntity();
  }
}
