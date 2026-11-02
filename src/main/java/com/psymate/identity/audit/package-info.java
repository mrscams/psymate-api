/**
 * Journal d'audit applicatif des accès patient (M7-02, G Q29) : {@code AuditFilter} dans la chaîne
 * de sécurité d'identity, {@code AuditWriter} qui écrit {@code security_event} hors du thread de la
 * requête. Venu de shared en M9-05 (ARCH-09) : la table appartient à identity.
 */
package com.psymate.identity.audit;
