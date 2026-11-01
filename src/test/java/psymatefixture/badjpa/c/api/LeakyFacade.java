package psymatefixture.badjpa.c.api;

import psymatefixture.badjpa.c.CEntity;

/** Violation : une facade du package api retourne une entite du module. */
public class LeakyFacade {
  public CEntity load() {
    return null;
  }
}
