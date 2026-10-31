package com.psymate.clinical.api;

import java.util.UUID;

/** Lien posé avec une nouvelle entrée : source et cible dans le sens réel du lien. */
public record CreatedLink(UUID id, UUID sourceId, UUID targetId, String linkType) {}
