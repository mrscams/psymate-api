package com.psymate.vocabulary;

import jakarta.persistence.EntityManager;

/** Aides SQL natif partagées par les fragments du module. */
final class NativeSql {

  private NativeSql() {}

  /** Vrai si la table existe dans le schéma public (to_regclass), faux sinon. */
  static boolean tableExists(EntityManager em, String table) {
    return (Boolean)
        em.createNativeQuery("select to_regclass(:t) is not null")
            .setParameter("t", "public." + table)
            .getSingleResult();
  }
}
