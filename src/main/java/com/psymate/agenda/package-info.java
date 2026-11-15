/**
 * Agenda du praticien (G Q50, docs/I-plan-agenda-api.md) : rendez-vous planifiés, sans
 * synchronisation externe en V1 (G Q51). Module JPA (G Q32) structuré par rôle dès le premier
 * fichier (G Q33). Il lit les autres modules par leurs packages {@code api} seulement ; {@code
 * directory} et {@code identity.admin} ne dépendent jamais de lui.
 */
@ApplicationModule(
    allowedDependencies = {
      "identity :: api",
      "patients :: api",
      "clinical :: api",
      "events :: api",
      "shared"
    })
package com.psymate.agenda;

import org.springframework.modulith.ApplicationModule;
