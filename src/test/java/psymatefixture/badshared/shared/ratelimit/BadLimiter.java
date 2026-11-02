package psymatefixture.badshared.shared.ratelimit;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Violation : shared fait du SQL. */
public class BadLimiter {
  JdbcClient jdbc;
}
