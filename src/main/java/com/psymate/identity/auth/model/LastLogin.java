package com.psymate.identity.auth.model;

import java.time.Instant;

/**
 * Connexion précédente d'un praticien, lue dans {@code security_event} ({@code login_ok}) : la
 * dernière antérieure à l'ouverture de la session courante. {@code device} est null pour un
 * événement antérieur à M10-06 ; {@code city} est toujours null en V1.
 */
public record LastLogin(Instant at, String device, String city) {}
