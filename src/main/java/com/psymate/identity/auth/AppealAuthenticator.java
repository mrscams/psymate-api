package com.psymate.identity.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Identification par identifiants d'un compte refusé ou suspendu qui dépose un recours (M1-14b,
 * fiche 27). Un tel compte n'a pas de session : le login le refuse (403 ACCOUNT_SUSPENDED). Même
 * verrou, même hachage factice et même 2FA que la connexion, via LoginService ; aucune session
 * n'est ouverte et aucune connexion n'est journalisée.
 */
@Service
public class AppealAuthenticator {

  /** Compte identifié ; ni hachage de mot de passe ni donnée 2FA. */
  public record AppealIdentity(
      UUID id,
      UUID tenantId,
      String email,
      String displayName,
      String locale,
      String verificationStatus) {}

  private final LoginService loginService;

  AppealAuthenticator(LoginService loginService) {
    this.loginService = loginService;
  }

  /**
   * Vérifie les identifiants. Renvoie le compte, ou vide quand la 2FA est active et le code absent
   * (l'appelant répond alors 202 otpRequired). Lève les exceptions d'authentification habituelles
   * (401, 423 avec Retry-After, 403 EMAIL_NOT_CONFIRMED) et 409 ACCOUNT_NOT_SUSPENDED si le compte
   * n'est ni refusé ni suspendu.
   */
  public Optional<AppealIdentity> verify(String email, String password, String otp, String ip) {
    LoginService.Outcome outcome = loginService.verifyForAppeal(email, password, otp, ip);
    if (outcome instanceof LoginService.Outcome.Authenticated a) {
      PractitionerRow row = a.practitioner();
      return Optional.of(
          new AppealIdentity(
              row.id(),
              row.tenantId(),
              row.email(),
              row.displayName(),
              row.locale(),
              row.verificationStatus()));
    }
    return Optional.empty();
  }
}
