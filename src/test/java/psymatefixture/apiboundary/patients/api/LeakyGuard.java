package psymatefixture.apiboundary.patients.api;

import org.springframework.web.server.ResponseStatusException;

/** Incorrect : une façade d'{@code api} lève un type Spring Web (ancienne garde, avant M9-04). */
public class LeakyGuard {
  public void requireOwned() {
    throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
  }
}
