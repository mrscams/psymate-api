package com.psymate.identity.auth;

import com.psymate.identity.api.PractitionerFacade;
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
}
