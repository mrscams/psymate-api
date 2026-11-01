package com.psymate.identity.api;

/**
 * Stockage d'objets chiffrés (AES-256-GCM, même stockage et même clé que les justificatifs) et
 * signature HMAC des URL, pour les autres modules (export du compte, M6-05). La clé de stockage est
 * préfixée par l'appelant (« exports/... ») et sert de donnée authentifiée du chiffrement.
 */
public interface EncryptedStorageFacade {

  /**
   * Chiffre et écrit l'objet.
   *
   * @throws IllegalArgumentException si la clé est vide, absolue ou contient « .. »
   */
  void put(String key, byte[] plain);

  /**
   * Contenu déchiffré.
   *
   * @throws java.io.UncheckedIOException si l'objet est absent
   * @throws IllegalArgumentException si la clé est invalide
   */
  byte[] get(String key);

  /** Idempotent : sans effet si l'objet n'existe pas. */
  void delete(String key);

  boolean exists(String key);

  /** HMAC-SHA256 en base64url, avec la clé des URL signées des justificatifs. */
  String sign(String data);
}
