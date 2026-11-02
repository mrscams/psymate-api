/**
 * API publique du module directory : seul package visible des autres modules. Elle ne contient que
 * les événements de publication du profil ; aucun module ne lit l'annuaire par façade (M9-06).
 */
@NamedInterface("api")
package com.psymate.directory.api;

import org.springframework.modulith.NamedInterface;
