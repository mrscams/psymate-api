package com.psymate.identity.auth;

import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.preferences.Preferences;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class PractitionerFacadeImpl implements PractitionerFacade {

  private final PractitionerRepository practitioners;
  private final JsonMapper json;

  private final JdbcClient jdbc;

  PractitionerFacadeImpl(PractitionerRepository practitioners, JsonMapper json, JdbcClient jdbc) {
    this.practitioners = practitioners;
    this.json = json;
    this.jdbc = jdbc;
  }

  @Override
  public Map<UUID, PractitionerView> findAll(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, PractitionerView> out = new HashMap<>();
    jdbc.sql(
            "select id, tenant_id, profession, verification_status from practitioner"
                + " where id = any(?)")
        .param(ids.toArray(UUID[]::new))
        .query(
            (rs, i) ->
                new PractitionerView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getString("profession"),
                    rs.getString("verification_status"),
                    Map.of()))
        .list()
        .forEach(v -> out.put(v.id(), v));
    return out;
  }

  @Override
  public Optional<String> professionalNumber(UUID id) {
    return jdbc.sql("select professional_number from practitioner where id = ?")
        .param(id)
        .query(String.class)
        .optional();
  }

  @Override
  public Optional<PractitionerView> find(UUID id) {
    return practitioners
        .findById(id)
        .map(
            r ->
                new PractitionerView(
                    r.id(),
                    r.tenantId(),
                    r.profession(),
                    r.verificationStatus(),
                    json.readValue(
                        r.preferencesJson(), new TypeReference<Map<String, Object>>() {})));
  }

  @Override
  public Map<String, Object> preferencesOf(UUID id) {
    return practitioners
        .findById(id)
        .map(
            r ->
                Preferences.effective(
                    r.profession(),
                    r.preferencesJson() == null
                        ? Map.<String, Object>of()
                        : json.readValue(
                            r.preferencesJson(), new TypeReference<Map<String, Object>>() {})))
        .orElse(Map.of());
  }
}
