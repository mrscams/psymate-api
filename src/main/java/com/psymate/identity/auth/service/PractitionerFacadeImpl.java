package com.psymate.identity.auth.service;

import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.auth.repository.PractitionerRepository;
import com.psymate.identity.preferences.Preferences;
import com.psymate.identity.repository.PractitionerJpaRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class PractitionerFacadeImpl implements PractitionerFacade {

  private final PractitionerRepository practitioners;
  private final PractitionerJpaRepository jpa;

  PractitionerFacadeImpl(PractitionerRepository practitioners, PractitionerJpaRepository jpa) {
    this.practitioners = practitioners;
    this.jpa = jpa;
  }

  @Override
  public Map<UUID, PractitionerView> findAll(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, PractitionerView> out = new HashMap<>();
    jpa.findAll(ids)
        .forEach(
            s ->
                out.put(
                    s.id(),
                    new PractitionerView(
                        s.id(), s.tenantId(), s.profession(), s.verificationStatus(), Map.of())));
    return out;
  }

  @Override
  public Optional<String> professionalNumber(UUID id) {
    return jpa.professionalNumber(id);
  }

  @Override
  public Optional<String> emailOf(UUID id) {
    return jpa.emailOf(id);
  }

  @Override
  public Optional<PractitionerView> find(UUID id) {
    return practitioners
        .findById(id)
        .map(
            r ->
                new PractitionerView(
                    r.id(), r.tenantId(), r.profession(), r.verificationStatus(), r.preferences()));
  }

  @Override
  public Map<String, Object> preferencesOf(UUID id) {
    return practitioners
        .findById(id)
        .map(r -> Preferences.effective(r.profession(), r.preferences()))
        .orElse(Map.of());
  }
}
