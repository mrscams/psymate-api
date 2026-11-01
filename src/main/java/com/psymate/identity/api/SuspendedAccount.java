package com.psymate.identity.api;

import java.util.UUID;

/**
 * Compte refusé ou suspendu identifié par ses identifiants (recours M1-14b, export du compte M6-05)
 * ; ni hachage de mot de passe ni donnée 2FA.
 */
public record SuspendedAccount(
    UUID id,
    UUID tenantId,
    String email,
    String displayName,
    String locale,
    String verificationStatus) {}
