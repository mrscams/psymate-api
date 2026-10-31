package com.psymate.identity.auth;

import java.util.LinkedHashMap;
import java.util.Map;

/** Corps d'inscription valides pour les tests d'intégration. */
final class RegistrationFixtures {

  static final String PASSWORD = "Zebre-Tournesol-42";

  private RegistrationFixtures() {}

  /** Inscription complète en cabinet (adresse renseignée). */
  static Map<String, Object> payload(String email, String password, String profession) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("email", email);
    p.put("password", password);
    p.put("firstName", "Maya");
    p.put("lastName", "Kaddour");
    p.put("profession", profession);
    p.put("phone", "06 12 34 56 78");
    p.put("address", Map.of("street", "12 rue des Lilas", "postalCode", "69003", "city", "Lyon"));
    p.put("remoteOnly", false);
    p.put("consents", Map.of("terms", true, "privacy", true));
    return p;
  }
}
