package com.psymate.shared.problem;

import java.util.List;
import org.springframework.http.HttpStatusCode;

/**
 * Erreur applicative portant directement son statut, son code et ses erreurs de champ : {@link
 * GlobalExceptionHandler} la rend telle quelle en application/problem+json. Les modules peuvent
 * l'adopter à la place de leurs exceptions maison (ils gardent pour l'instant leurs propres
 * handlers, qui restent prioritaires). Sous-classes : les erreurs de clinical.export et {@code
 * patients.api.PatientAccessException}, levée par la garde de propriété des patients.
 */
public class ProblemException extends RuntimeException {

  private final HttpStatusCode status;
  private final String code;
  private final String title;
  private final List<FieldProblem> errors;

  public ProblemException(HttpStatusCode status, String code, String title, String detail) {
    this(status, code, title, detail, List.of());
  }

  /**
   * Statut en entier et titre usuel du statut (« Forbidden », « Not Found »…) : pour une exception
   * déclarée dans un package {@code api}, qui ne dépend d'aucun type {@code
   * org.springframework.http} ({@code ApiBoundaryTests}).
   */
  public ProblemException(int status, String code, String detail) {
    this(
        HttpStatusCode.valueOf(status),
        code,
        Problems.titleOf(HttpStatusCode.valueOf(status)),
        detail,
        List.of());
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
