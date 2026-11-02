package com.psymate.identity.verification.service;

/**
 * Stockage d'objets opaques (déjà chiffrés par l'appelant). Dev : disque local ; bêta : décidé avec
 * l'étude hébergement (G Q17).
 */
public interface StorageService {

  void put(String key, byte[] data);

  /** Contenu stocké, ou {@link java.io.UncheckedIOException} s'il est absent. */
  byte[] get(String key);

  /** Idempotent : sans effet si l'objet n'existe pas. */
  void delete(String key);

  boolean exists(String key);
}
