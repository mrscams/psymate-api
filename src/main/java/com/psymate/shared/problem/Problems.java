package com.psymate.shared.problem;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Fabrique des réponses d'erreur RFC 9457 de PsyMate : {@code type} about:blank, {@code title},
 * {@code status}, {@code detail}, {@code code} et, si besoin, {@code errors[]}. Utilisable par les
 * handlers MVC (le {@code ProblemDetail} est renvoyé tel quel) comme par les filtres et entry
 * points (voir {@link ProblemWriter}). {@code instance} n'est pas posé ici : Spring MVC le
 * renseigne avec le chemin de la requête quand le {@code ProblemDetail} est rendu par un handler.
 */
public final class Problems {

  private Problems() {}

  public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
    return of(status, code, title, detail, List.of());
  }

  public static ProblemDetail of(
      HttpStatusCode status, String code, String title, String detail, List<FieldProblem> errors) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setTitle(title);
    pd.setProperty("code", code);
    if (errors != null && !errors.isEmpty()) {
      pd.setProperty("errors", errors);
    }
    return pd;
  }

  /**
   * Titre usuel d'un statut (« Not Found », « Too Many Requests »…), comme les handlers de module.
   */
  public static String titleOf(HttpStatusCode status) {
    HttpStatus resolved = HttpStatus.resolve(status.value());
    return resolved != null ? resolved.getReasonPhrase() : "Error";
  }
}
