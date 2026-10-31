package com.psymate.identity.api;

import java.io.Serializable;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Praticien authentifié de la requête courante (principal de la session). */
public record CurrentPractitioner(
    UUID id,
    UUID tenantId,
    String email,
    String displayName,
    String profession,
    String verificationStatus)
    implements Principal, Serializable {

  /** Le nom du principal est l'identifiant du praticien (indexé dans spring_session). */
  @Override
  public String getName() {
    return id.toString();
  }

  /** Praticien de la requête courante, vide si la requête n'est pas authentifiée. */
  public static Optional<CurrentPractitioner> get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof CurrentPractitioner p) {
      return Optional.of(p);
    }
    return Optional.empty();
  }
}
