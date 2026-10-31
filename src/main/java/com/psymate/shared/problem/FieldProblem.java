package com.psymate.shared.problem;

/** Une entrée de {@code errors[]} : le champ fautif, un code stable et un message lisible. */
public record FieldProblem(String field, String code, String message) {}
