package com.psymate.vocabulary;

import java.util.UUID;

/**
 * Port interne : praticien et tenant courants. Aucune implémentation de production pour l'instant ;
 * elle sera branchée sur com.psymate.identity.api après M0-04, qui apporte aussi la sécurité
 * protégeant tous les endpoints de ce module. Les tests fournissent leur propre implémentation.
 */
public interface CurrentPractitionerPort {

  UUID practitionerId();

  UUID tenantId();
}
