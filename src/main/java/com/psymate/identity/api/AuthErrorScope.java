package com.psymate.identity.api;

/**
 * Marqueur pour un contrôleur, de identity ou d'un autre module (export du compte de clinical,
 * M6-05), qui authentifie par identifiants (recours du compte suspendu, M1-14b) : le handler
 * d'erreurs d'authentification de identity lui rend les erreurs (401, 403, 409
 * ACCOUNT_NOT_SUSPENDED, 423 avec Retry-After) en RFC 9457. Déplacé de identity.auth vers
 * identity.api en M6-05 pour que les autres modules puissent le porter.
 */
public interface AuthErrorScope {}
