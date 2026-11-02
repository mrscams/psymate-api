package psymatefixture.badshared.shared.audit;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Correct ici : exception passee a la regle (comme shared.audit jusqu'a M9-05). */
public class AuditLike {
  JdbcClient jdbc;
}
