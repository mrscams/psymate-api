package com.psymate.linking.model;

import java.util.List;

/** Paramètres de graphe refusés : 422 VALIDATION_FAILED avec une entrée errors[] par champ. */
public class GraphRequestException extends RuntimeException {

  /**
   * Un champ fautif et un code stable (OUT_OF_RANGE, INVALID_VALUE, INVALID_TIME, INVALID_FORMAT,
   * REQUIRED, ENTRY_NOT_FOUND).
   */
  public record Problem(String field, String code) {}

  private final transient List<Problem> problems;

  public GraphRequestException(List<Problem> problems) {
    super("Requête de graphe invalide : " + problems);
    this.problems = List.copyOf(problems);
  }

  public GraphRequestException(String field, String code) {
    this(List.of(new Problem(field, code)));
  }

  public List<Problem> problems() {
    return problems;
  }
}
