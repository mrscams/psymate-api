package psymatefixture.shareddb;

import org.springframework.jdbc.core.JdbcTemplate;

/** Correct : supprime ses propres lignes. */
public class DeletesOwnRows {
  void clean(JdbcTemplate jdbc) {
    jdbc.update("delete from event_publication where listener_id = 'test.purge'");
  }
}
