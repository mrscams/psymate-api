package com.psymate.seed;

/** Erreur d'exécution du seed (code de sortie 1), avec un message lisible en français. */
public class SeedException extends RuntimeException {

  public SeedException(String message) {
    super(message);
  }

  public SeedException(String message, Throwable cause) {
    super(message, cause);
  }
}
