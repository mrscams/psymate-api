package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminException.FieldError;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Curseur de pagination opaque des listes d'administration : base64url("epochMillis|uuid"), comme
 * la file de vérification (M1-14). La clé de tri est tronquée à la milliseconde.
 */
final class AdminCursors {

  static final int DEFAULT_LIMIT = 50;
  static final int MAX_LIMIT = 200;

  /** Position décodée : clé de tri en millisecondes et identifiant. */
  record Position(long key, UUID id) {}

  private AdminCursors() {}

  static int limit(Integer requested) {
    return requested == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requested, MAX_LIMIT));
  }

  /** Null pour la première page ; 422 INVALID_CURSOR si le curseur est illisible. */
  static Position decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String[] parts =
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
      if (parts.length != 2) {
        throw new IllegalArgumentException("cursor");
      }
      return new Position(Long.parseLong(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException e) {
      throw AdminException.fields(new FieldError("cursor", "INVALID_CURSOR", "admin.error.cursor"));
    }
  }

  static String encode(Instant sortKey, UUID id) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((sortKey.toEpochMilli() + "|" + id).getBytes(StandardCharsets.UTF_8));
  }
}
