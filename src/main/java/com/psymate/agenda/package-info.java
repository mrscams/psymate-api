/**
 * Agenda du praticien (G Q50, docs/I-plan-agenda-api.md) : rendez-vous planifiés, sans
 * synchronisation externe en V1 (G Q51). Module JPA (G Q32) structuré par rôle dès le premier
 * fichier (G Q33). Il lit les autres modules par leurs packages {@code api} seulement ; {@code
 * directory} et {@code identity.admin} ne dépendent jamais de lui. Il ne dépend pas de {@code
 * clinical} (G Q61) : c'est l'export de {@code clinical} qui lit sa façade {@code api}.
 */
@ApplicationModule(
    allowedDependencies = {"identity :: api", "patients :: api", "events :: api", "shared"})
package com.psymate.agenda;

import org.springframework.modulith.ApplicationModule;
