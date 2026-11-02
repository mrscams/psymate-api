package com.psymate.patients.model;

/**
 * Filtre de nom de la liste : motif LIKE à comparer à {@code f_unaccent(display_name)} avec ILIKE
 * (index trigram). L'accent est retiré côté SQL par f_unaccent sur le motif ; ici on nettoie et on
 * échappe les jokers LIKE.
 */
public final class NameFilter {

  private NameFilter() {}

  /** Motif « contient », ou null si la recherche est vide. */
  public static String pattern(String q) {
    if (q == null) {
      return null;
    }
    String clean = q.trim().replaceAll("\\s+", " ");
    if (clean.isEmpty()) {
      return null;
    }
    String escaped = clean.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
