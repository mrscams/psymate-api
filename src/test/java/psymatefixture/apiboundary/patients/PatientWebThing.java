package psymatefixture.apiboundary.patients;

import org.springframework.http.ProblemDetail;

/** Correct : hors {@code api}, un module peut dépendre de Spring Web. */
public class PatientWebThing {
  public ProblemDetail problem() {
    return null;
  }
}
