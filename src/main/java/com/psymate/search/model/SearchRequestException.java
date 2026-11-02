package com.psymate.search.model;

import com.psymate.shared.problem.FieldProblem;
import java.util.List;

/**
 * Paramètres de recherche invalides : un problème par champ (400, SEARCH_BAD_REQUEST), sans message
 * (clé search.error.CODE des messages, ajoutée par SearchExceptionHandler).
 */
public class SearchRequestException extends RuntimeException {

  private final transient List<FieldProblem> problems;

  public SearchRequestException(List<FieldProblem> problems) {
    super("Paramètres de recherche invalides : " + problems);
    this.problems = List.copyOf(problems);
  }

  public List<FieldProblem> problems() {
    return problems;
  }
}
