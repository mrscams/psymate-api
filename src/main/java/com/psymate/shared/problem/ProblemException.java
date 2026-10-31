package com.psymate.shared.problem;

import java.util.List;
import org.springframework.http.HttpStatusCode;

/**
 * Erreur applicative portant directement son statut, son code et ses erreurs de champ : {@link
 * GlobalExceptionHandler} la rend telle quelle en application/problem+json. Les modules peuvent
 * l'adopter à la place de leurs exceptions maison (ils gardent pour l'instant leurs propres
 * handlers, qui restent prioritaires).
 */
public class ProblemException extends RuntimeException {

  private final HttpStatusCode status;
  private final String code;
  private final String title;
  private final List<FieldProblem> errors;

  public ProblemException(HttpStatusCode status, String code, String title, String detail) {
    this(status, code, title, detail, List.of());
  }

  public ProblemException(
      HttpStatusCode status, String code, String title, String detail, List<FieldProblem> errors) {
    super(detail);
    this.status = status;
    this.code = code;
    this.title = title;
    this.errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public HttpStatusCode status() {
    return status;
  }

  public String code() {
    return code;
  }

  public String title() {
    return title;
  }

  public String detail() {
    return getMessage();
  }

  public List<FieldProblem> errors() {
    return errors;
  }
}
