package com.psymate.identity.auth;

/**
 * Marqueur pour un contrôleur d'un autre package du module qui authentifie par identifiants
 * (recours du compte suspendu, M1-14b) : AuthExceptionHandler lui rend les erreurs
 * d'authentification (401, 403, 409 ACCOUNT_NOT_SUSPENDED, 423 avec Retry-After) en RFC 9457.
 */
public interface AuthErrorScope {}
