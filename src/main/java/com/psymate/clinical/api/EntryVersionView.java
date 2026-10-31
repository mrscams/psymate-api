package com.psymate.clinical.api;

import java.time.Instant;
import java.util.UUID;

/** Version courante d'une entrée, exposée aux autres modules. */
public record EntryVersionView(
    UUID id, UUID entryId, int version, String title, String body, Instant createdAt) {}
