/**
 * Facturation du praticien (G Q83 à Q95, docs/L-plan-facturation-api.md) : réglages, prestations,
 * profil de facturation d'un patient, séances à facturer, factures, avoirs, paiements. Module JPA
 * (G Q32) structuré par rôle dès le premier fichier (G Q33). Il lit les autres modules par leurs
 * packages {@code api} seulement : {@code identity}, {@code patients}, {@code clinical} et {@code
 * events} ; il ne lit aucune table d'un autre module (Q85). Aucun module ne dépend de lui : il n'a
 * pas de package {@code api}, et ni {@code search} ni {@code overview} ne lisent ses tables (Q83).
 * Il ne stocke aucun texte clinique. Montants en centimes d'euro entiers, en base comme en JSON
 * (Q84). Ni nom de patient, ni montant, ni IBAN, ni adresse e-mail dans les journaux (Q95).
 */
@ApplicationModule(
    allowedDependencies = {
      "identity :: api",
      "patients :: api",
      "clinical :: api",
      "events :: api",
      "shared"
    })
package com.psymate.billing;

import org.springframework.modulith.ApplicationModule;
