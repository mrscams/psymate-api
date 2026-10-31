package com.psymate.identity.auth;

import com.psymate.identity.api.CurrentPractitioner;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Charge un praticien par e-mail (normalisé) pour l'authentification par mot de passe. */
@Service
class PractitionerUserDetailsService implements UserDetailsService {

  private final PractitionerRepository practitioners;

  PractitionerUserDetailsService(PractitionerRepository practitioners) {
    this.practitioners = practitioners;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    PractitionerRow r =
        practitioners
            .findByEmailNormalized(AuthService.normalizeEmail(username))
            .orElseThrow(() -> new UsernameNotFoundException("unknown"));
    CurrentPractitioner current =
        new CurrentPractitioner(
            r.id(),
            r.tenantId(),
            r.email(),
            r.displayName(),
            r.profession(),
            r.verificationStatus());
    return new PractitionerPrincipal(current, r.role(), r.passwordHash());
  }
}
