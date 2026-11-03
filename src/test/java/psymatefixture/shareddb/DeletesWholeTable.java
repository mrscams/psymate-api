package psymatefixture.shareddb;

import org.springframework.jdbc.core.JdbcTemplate;

/** Interdit : vide la table de tous les tests. */
public class DeletesWholeTable {
  void clean(JdbcTemplate jdbc) {
    jdbc.update("delete from contact_request");
  }
}
