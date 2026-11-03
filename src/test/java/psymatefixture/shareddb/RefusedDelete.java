package psymatefixture.shareddb;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.jdbc.core.JdbcTemplate;

/** Correct : le test vérifie que la suppression est refusée, rien n'est supprimé. */
public class RefusedDelete {
  void check(JdbcTemplate jdbc, String table) {
    assertThatThrownBy(() -> jdbc.update("delete from " + table))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> jdbc.update("truncate entry_version"))
        .isInstanceOf(RuntimeException.class);
  }
}
