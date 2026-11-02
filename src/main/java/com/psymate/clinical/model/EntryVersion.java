package com.psymate.clinical.model;

import java.time.Instant;
import java.util.UUID;

/** Une version append-only d'une entrée (A4.8). */
public record EntryVersion(
    UUID id,
    UUID entryId,
    int version,
    String title,
    String body,
    String payload,
    String reason,
    UUID authorId,
    Instant createdAt) {}
