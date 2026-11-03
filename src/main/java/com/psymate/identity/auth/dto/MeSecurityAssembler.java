package com.psymate.identity.auth.dto;

import com.psymate.identity.auth.model.SecurityOverview;

/** Mapping écrit à la main de la vue de sécurité du compte vers sa réponse (M10-06). */
public final class MeSecurityAssembler {

  private MeSecurityAssembler() {}

  public static MeSecurityResponse toResponse(SecurityOverview overview) {
    return new MeSecurityResponse(
        overview.sessions().stream()
            .map(
                s ->
                    new ActiveSessionResponse(
                        s.id(),
                        s.createdAt(),
                        s.lastAccessAt(),
                        s.expiresAt(),
                        s.current(),
                        s.device(),
                        s.city()))
            .toList(),
        overview.lastLogin() == null
            ? null
            : new LastLoginResponse(
                overview.lastLogin().at(),
                overview.lastLogin().device(),
                overview.lastLogin().city()));
  }
}
