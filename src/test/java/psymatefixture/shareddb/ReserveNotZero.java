package psymatefixture.shareddb;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.jdbc.core.JdbcTemplate;

/** Interdit : une réserve comptée sans isZero est un compte de table entière. */
public class ReserveNotZero {
  void check(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForObject("select count(*) from ai_feedback", Integer.class)).isEqualTo(0);
  }
}
