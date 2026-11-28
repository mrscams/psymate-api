package com.psymate.billing.model;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * Curseur opaque de la liste des factures : position du dernier élément d'une page dans l'ordre de
 * L5.3 (brouillons d'abord, puis date d'émission décroissante, puis identifiant), encodée en
 * base64url de {@code rang|AAAA-MM-JJ|uuid}. Un curseur illisible est refusé par l'appelant (422
 * cursor INVALID_FORMAT).
 *
 * @param rank 0 pour un brouillon, 1 sinon
 * @param issueDate date d'émission du dernier élément
 * @param id identifiant du dernier élément
 */
public record InvoiceCursor(int rank, LocalDate issueDate, UUID id) {

  /** Texte opaque du curseur. */
  public String encode() {
    String raw = rank + "|" + issueDate + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Curseur décodé ; {@link IllegalArgumentException} s'il est illisible (base64, forme, rang, date
   * ou identifiant).
   */
  public static InvoiceCursor decode(String cursor) {
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|", -1);
      if (parts.length != 3) {
        throw new IllegalArgumentException("cursor");
      }
      int rank = Integer.parseInt(parts[0]);
      if (rank != 0 && rank != 1) {
        throw new IllegalArgumentException("cursor");
      }
      return new InvoiceCursor(rank, LocalDate.parse(parts[1]), UUID.fromString(parts[2]));
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("cursor", e);
    }
  }
}
