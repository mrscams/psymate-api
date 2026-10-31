package com.psymate.vocabulary.api;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Tag libre d'un praticien. */
public record TagView(
    UUID id, String label, String normalizedLabel, int usageCount, OffsetDateTime createdAt) {}
