/**
 * Administration : validation des praticiens (M1-14), liste des comptes, suspension et réactivation
 * manuelles, journal d'administration en lecture seule et recours des comptes suspendus (M1-14b).
 * Ne dépend que d'identity et events ; aucun accès aux données patient (ArchUnit).
 *
 * <p>Les corps de réponse et de requête ({@code dto}) ne portent jamais de clé de stockage,
 * d'empreinte, de hachage de mot de passe, de donnée 2FA, d'adresse IP ni de donnée patient.
 */
package com.psymate.identity.admin;
