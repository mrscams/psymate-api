package com.psymate.identity.api;

import java.util.Optional;

/**
 * Identification par identifiants d'un compte refusé ou suspendu (fiche 27). Un tel compte n'a pas
 * de session : le login le refuse (403 ACCOUNT_SUSPENDED). Même verrou, même hachage factice et
 * même 2FA que la connexion ; aucune session n'est ouverte et aucune connexion n'est journalisée.
 */
public interface SuspendedAccountAuthenticator {

  /**
   * Vérifie les identifiants. Renvoie le compte, ou vide quand la 2FA est active et le code absent
   * (l'appelant répond alors 202 otpRequired). Lève les exceptions d'authentification habituelles
   * (401 INVALID_CREDENTIALS ou code 2FA faux, 423 avec Retry-After, 403 EMAIL_NOT_CONFIRMED) et
   * 409 ACCOUNT_NOT_SUSPENDED si le compte n'est ni refusé ni suspendu. Ces erreurs sont rendues en
   * RFC 9457 pour tout contrôleur qui porte {@link AuthErrorScope}.
   *
   * @param ip adresse du client, clé du verrou de connexion
   */
  Optional<SuspendedAccount> verify(String email, String password, String otp, String ip);
}
