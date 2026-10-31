package com.psymate.clinical.web;

import java.util.List;

/** Corps de requête refusé : 422 avec une entrée errors[] par champ fautif. */
class EntryRequestException extends RuntimeException {

  private final transient List<FieldProblem> problems;

  EntryRequestException(List<FieldProblem> problems) {
    super("Requête invalide : " + problems);
    this.problems = List.copyOf(problems);
  }

  List<FieldProblem> problems() {
    return problems;
  }
}
