/**
 * API publique du module directory : seul package visible des autres modules. Elle contient les
 * événements de publication du profil et une seule façade de lecture, {@link
 * PublishedProfileFacade} (fiche publiée d'un praticien approuvé, réservée à la prise de
 * rendez-vous en ligne d'agenda, G Q71) ; aucune entité ni aucun repository n'y est exposé.
 */
@NamedInterface("api")
package com.psymate.directory.api;

import org.springframework.modulith.NamedInterface;
