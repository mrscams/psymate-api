package com.psymate.vocabulary;

import java.util.UUID;

/**
 * Port interne : praticien et tenant courants. Implémenté sur com.psymate.identity.api (principal
 * de la session) ; les tests authentifient leurs requêtes plutôt que de remplacer le port.
 */
public interface CurrentPractitionerPort {

  UUID practitionerId();

  UUID tenantId();
}
