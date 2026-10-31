package com.psymate.shared.problem;

import org.springframework.core.Ordered;

/**
 * Précédence des {@code @RestControllerAdvice}. Spring trie les advices par {@code @Order} ; à
 * égalité, c'est l'ordre d'enregistrement des beans (le scan, alphabétique en développement, non
 * garanti dans un jar) qui décide. Sans ordre explicite, un handler de module placé après {@code
 * shared} dans le scan ({@code vocabulary}) serait doublé par {@link GlobalExceptionHandler}, qui
 * attrape tout. Tout advice de module porte donc {@code @Order(AdviceOrder.MODULE)}, strictement
 * avant le global ({@link Ordered#LOWEST_PRECEDENCE}) ; {@code ArchitectureTests} le vérifie.
 */
public final class AdviceOrder {

  /** Précédence des advices limités à un ou plusieurs contrôleurs d'un module. */
  public static final int MODULE = Ordered.LOWEST_PRECEDENCE - 1;

  private AdviceOrder() {}
}
