package psymatefixture.badjpa.c.web;

import org.springframework.web.bind.annotation.RestController;
import psymatefixture.badjpa.c.CEntity;

/** Violation : une classe web qui retourne une entite. */
@RestController
public class LeakyController {
  public CEntity get() {
    return new CEntity();
  }
}
