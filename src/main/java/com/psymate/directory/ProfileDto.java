package com.psymate.directory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Profil public tel que le voit son propriétaire. */
record ProfileDto(
    String slug,
    boolean published,
    String displayName,
    String profession,
    String titleLabel,
    boolean showProfessionalNumber,
    String photoAssetKey,
    String bio,
    List<String> specialties,
    List<String> audiences,
    List<String> modalities,
    List<String> languages,
    String city,
    String postalCode,
    String country,
    String contactPhone,
    String contactEmail,
    String websiteUrl,
    String address,
    boolean showPhone,
    boolean showEmail,
    boolean showAddress,
    String responseDelay,
    String fees,
    String availability,
    LocalDate nextSlotAt,
    OffsetDateTime publishedAt,
    OffsetDateTime updatedAt) {}
