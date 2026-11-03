package psymatefixture.shareddb;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.jdbc.core.JdbcTemplate;

/** Correct : invariant « table vide » d'une réserve, sur toute la table. */
public class ReserveEmpty {
  void check(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForObject("select count(*) from ai_feedback", Integer.class)).isZero();
  }
}
