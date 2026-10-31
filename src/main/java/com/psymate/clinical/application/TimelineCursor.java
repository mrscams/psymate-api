package com.psymate.clinical.application;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Curseur keyset de la timeline sur (occurred_from, id). Encodé en Base64 URL-safe sans padding de
 * « instant|uuid » ; l'instant garde sa précision (microseconde de timestamptz), jamais arrondi à
 * la milliseconde.
 */
public record TimelineCursor(Instant occurredFrom, UUID id) {

  private static final char SEPARATOR = '|';
  private static final int UUID_LENGTH = 36;

  public TimelineCursor {
    if (occurredFrom == null || id == null) {
      throw new IllegalArgumentException("Curseur incomplet");
    }
  }

  public String encode() {
    String raw = occurredFrom + String.valueOf(SEPARATOR) + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Relit un curseur produit par {@link #encode()}.
   *
   * @throws IllegalArgumentException base64, format, instant ou uuid illisible
   */
  public static TimelineCursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      throw new IllegalArgumentException("Curseur vide");
    }
    try {
      String raw =
          new String(Base64.getUrlDecoder().decode(cursor.strip()), StandardCharsets.UTF_8);
      int sep = raw.indexOf(SEPARATOR);
      if (sep <= 0 || raw.length() - sep - 1 != UUID_LENGTH) {
        throw new IllegalArgumentException("Format de curseur inattendu");
      }
      return new TimelineCursor(
          Instant.parse(raw.substring(0, sep)), UUID.fromString(raw.substring(sep + 1)));
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("Instant de curseur illisible", e);
    }
  }
}
