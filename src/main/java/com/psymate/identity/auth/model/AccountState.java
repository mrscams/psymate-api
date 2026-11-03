package com.psymate.identity.auth.model;

/**
 * État de sécurité d'un compte, relu en base à chaque appel (jamais figé dans la session) : rôle
 * ({@code practitioner} ou {@code admin}), double authentification active, changement de mot de
 * passe imposé (compte de bootstrap, G Q5).
 */
public record AccountState(String role, boolean twoFactorEnabled, boolean passwordChangeRequired) {}
