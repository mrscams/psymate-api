package com.psymate.identity.auth.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Session active d'un praticien (M10-06). {@code id} est un identifiant opaque : la clé interne de
 * la ligne {@code spring_session}, jamais l'identifiant HTTP que porte le cookie. {@code device}
 * est null pour une session ouverte avant M10-06 ou dont l'User-Agent n'a rien livré ; {@code city}
 * est toujours null en V1 (aucune géolocalisation).
 */
public record ActiveSession(
    UUID id,
    Instant createdAt,
    Instant lastAccessAt,
    Instant expiresAt,
    boolean current,
    String device,
    String city) {}
