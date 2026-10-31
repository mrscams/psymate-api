package com.psymate.identity.auth;

import com.psymate.identity.api.PractitionerFacade;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class PractitionerFacadeImpl implements PractitionerFacade {

  private final PractitionerRepository practitioners;
  private final JsonMapper json;

  PractitionerFacadeImpl(PractitionerRepository practitioners, JsonMapper json) {
    this.practitioners = practitioners;
    this.json = json;
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
