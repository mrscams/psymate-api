package com.psymate.identity.preferences;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PreferencesRepository {

  /** Cabinet, profession et préférences stockées (JSON brut) d'un praticien. */
  record Row(UUID tenantId, String profession, String preferencesJson) {}

  private static final String SELECT =
      "select tenant_id, profession, preferences::text as preferences from practitioner"
          + " where id = :id";

  private final JdbcClient jdbc;

  PreferencesRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Lecture avec verrou de ligne : sérialise les PATCH concurrents d'un même compte. */
  Optional<Row> lockById(UUID id) {
    return select(SELECT + " for update", id);
  }

  Optional<Row> findById(UUID id) {
    return select(SELECT, id);
  }

  private Optional<Row> select(String sql, UUID id) {
    return jdbc.sql(sql)
        .param("id", id)
        .query(
            (rs, i) ->
                new Row(
                    rs.getObject("tenant_id", UUID.class),
                    rs.getString("profession"),
                    rs.getString("preferences")))
        .optional();
  }

  void update(UUID id, String json) {
    jdbc.sql("update practitioner set preferences = cast(:p as jsonb) where id = :id")
        .param("p", json)
        .param("id", id)
        .update();
  }
}
