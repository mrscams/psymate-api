package psymatefixture.badjpa.c.flat;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import psymatefixture.badjpa.c.CEntity;

/** Violation : un gestionnaire d'erreurs hors package web qui manipule une entite. */
@RestControllerAdvice
public class LeakyAdvice {
  public String handle(CEntity entity) {
    return "";
  }
}
