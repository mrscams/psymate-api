package com.psymate.identity.repository;

import org.hibernate.JDBCException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * Reconnaît un conflit d'unicité PostgreSQL (SQLSTATE 23505) levé au flush ou au commit : la
 * traduction Spring ne donne pas toujours {@link DuplicateKeyException} avec Hibernate, la chaîne
 * de causes est donc parcourue.
 */
public final class UniqueViolations {

  private static final String UNIQUE_VIOLATION = "23505";

  private UniqueViolations() {}

  public static boolean isUniqueViolation(DataIntegrityViolationException e) {
    for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
      if (t instanceof DuplicateKeyException) {
        return true;
      }
      if (t instanceof JDBCException j && UNIQUE_VIOLATION.equals(j.getSQLState())) {
        return true;
      }
    }
    return false;
  }
}
