package com.psymate.identity.auth;

import com.psymate.identity.api.CurrentPractitioner;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** UserDetails interne servant uniquement à l'authentification par mot de passe. */
class PractitionerPrincipal implements UserDetails, CredentialsContainer {

  private final CurrentPractitioner current;
  private final String role;
  private String passwordHash;

  PractitionerPrincipal(CurrentPractitioner current, String role, String passwordHash) {
    this.current = current;
    this.role = role;
    this.passwordHash = passwordHash;
  }

  CurrentPractitioner current() {
    return current;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return current.getName();
  }

  @Override
  public void eraseCredentials() {
    this.passwordHash = null;
  }
}
