package com.psymate.directory;

import java.util.UUID;

/** Stockage des photos de profil (objets déjà redimensionnés, sans EXIF). */
interface PhotoStorage {

  /** Enregistre l'image et renvoie sa clé de stockage. */
  String store(UUID practitionerId, byte[] jpeg);

  byte[] read(String key);

  void delete(String key);
}
