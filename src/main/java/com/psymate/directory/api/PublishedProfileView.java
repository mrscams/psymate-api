package com.psymate.directory.api;

import java.util.UUID;

/**
 * Fiche publiée d'un praticien approuvé, réduite à son identité publique (G Q71) : ni coordonnées,
 * ni photo. {@code tenantId} est celui du praticien, lu par {@code PractitionerFacade}.
 */
public record PublishedProfileView(
    UUID practitionerId, UUID tenantId, String slug, String displayName) {}
