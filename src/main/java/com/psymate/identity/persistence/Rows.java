package com.psymate.identity.persistence;

import jakarta.persistence.Tuple;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lecture typée des lignes des requêtes natives (Tuple, colonnes lues par alias). Hibernate rend un
 * {@code timestamptz} natif sous la forme que le pilote déclare ({@code Timestamp}, {@code
 * OffsetDateTime} ou {@code Instant}) : {@link #instant} accepte les trois.
 */
public final class Rows {

  private Rows() {}

  public static UUID uuid(Tuple row, String alias) {
    return (UUID) row.get(alias);
  }

  public static String string(Tuple row, String alias) {
    return (String) row.get(alias);
  }

  public static boolean bool(Tuple row, String alias) {
    return Boolean.TRUE.equals(row.get(alias));
  }

  public static int integer(Tuple row, String alias) {
    Object value = row.get(alias);
    return value == null ? 0 : ((Number) value).intValue();
  }

  public static Instant instant(Tuple row, String alias) {
    Object value = row.get(alias);
    if (value == null) {
      return null;
    }
    if (value instanceof Instant i) {
      return i;
    }
    if (value instanceof OffsetDateTime o) {
      return o.toInstant();
    }
    if (value instanceof java.util.Date d) {
      return d.toInstant();
    }
    throw new IllegalStateException(
        "Type de date inattendu pour " + alias + " : " + value.getClass().getName());
  }
}
