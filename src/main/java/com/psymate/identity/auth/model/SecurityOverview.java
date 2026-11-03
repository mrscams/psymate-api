package com.psymate.identity.auth.model;

import java.util.List;

/** Sessions actives et dernière connexion d'un praticien (GET /api/me/security, M10-06). */
public record SecurityOverview(List<ActiveSession> sessions, LastLogin lastLogin) {}
