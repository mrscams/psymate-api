package psymatefixture.badshared.shared.audit;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Violation : du SQL dans shared, meme sous audit (exception retiree en M9-05). */
public class AuditLike {
  JdbcClient jdbc;
}
