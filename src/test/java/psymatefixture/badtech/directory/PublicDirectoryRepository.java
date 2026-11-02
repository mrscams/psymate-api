package psymatefixture.badtech.directory;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Correct : la recherche publique de l'annuaire reste en JdbcClient (G Q38, liste autorisee). */
public class PublicDirectoryRepository {
  JdbcClient jdbc;

  /** Correct aussi : une classe imbriquee suit sa classe englobante. */
  static class Row {
    org.springframework.jdbc.core.RowMapper<String> mapper;
  }
}
