package com.psymate.shared.problem;

/**
 * Une entrée de {@code errors[]} : le champ fautif, un code stable et un message lisible. Seul type
 * d'erreur de champ de l'API (schéma {@code FieldProblem} du contrat, écrit dans {@code
 * OpenApiConfig}). Un module qui localise ses messages au moment de répondre construit d'abord
 * l'erreur sans message ({@link #FieldProblem(String, String)}), puis la complète dans son handler.
 */
public record FieldProblem(String field, String code, String message) {

  /** Erreur de champ dont le message (localisé) sera ajouté par le handler. */
  public FieldProblem(String field, String code) {
    this(field, code, null);
  }
}
