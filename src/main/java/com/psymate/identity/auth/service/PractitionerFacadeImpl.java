package com.psymate.identity.auth.service;

import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.auth.model.PractitionerRow;
import com.psymate.identity.auth.repository.PractitionerRepository;
import com.psymate.identity.preferences.Preferences;
import com.psymate.identity.repository.PractitionerJpaRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class PractitionerFacadeImpl implements PractitionerFacade {

  private final PractitionerRepository practitioners;
  private final PractitionerJpaRepository jpa;
  private final PasswordEncoder encoder;

  PractitionerFacadeImpl(
      PractitionerRepository practitioners,
      PractitionerJpaRepository jpa,
      PasswordEncoder encoder) {
    this.practitioners = practitioners;
    this.jpa = jpa;
    this.encoder = encoder;
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
  public Optional<IssuerView> issuerOf(UUID id) {
    return jpa.findById(id)
        .map(
            p ->
                new IssuerView(
                    p.getId(),
                    p.getFirstName(),
                    p.getLastName(),
                    p.getDisplayName(),
                    p.getProfession(),
                    p.getProfessionalNumber(),
                    p.getAddressStreet(),
                    p.getAddressPostalCode(),
                    p.getAddressCity(),
                    p.getPhone(),
                    p.getEmail()));
  }

  /**
   * Même vérification que le changement d'e-mail ou la désactivation de la 2FA ({@code
   * encoder.matches} sur le haché du compte), sans {@link LoginAttemptLimiter}, sans écriture et
   * sans journal.
   */
  @Override
  public boolean passwordMatches(UUID id, CharSequence password) {
    if (id == null || password == null || password.isEmpty()) {
      return false;
    }
    return practitioners
        .findById(id)
        .map(PractitionerRow::passwordHash)
        .filter(hash -> !hash.isBlank())
        .map(hash -> encoder.matches(password, hash))
        .orElse(false);
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
