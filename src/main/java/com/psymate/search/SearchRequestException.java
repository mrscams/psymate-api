package com.psymate.search;

import java.util.List;

/** Paramètres de recherche invalides : un problème par champ (400, SEARCH_BAD_REQUEST). */
class SearchRequestException extends RuntimeException {

  /** Un champ fautif et son code d'erreur (clé search.error.CODE des messages). */
  record FieldProblem(String field, String code) {}

  private final transient List<FieldProblem> problems;

  SearchRequestException(List<FieldProblem> problems) {
    super("Paramètres de recherche invalides : " + problems);
    this.problems = List.copyOf(problems);
  }

  List<FieldProblem> problems() {
    return problems;
  }
}
