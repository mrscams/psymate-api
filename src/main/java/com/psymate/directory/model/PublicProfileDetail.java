package com.psymate.directory.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Profil publié tel que lu par {@code PublicDirectoryRepository} pour la fiche publique, avant les
 * règles d'affichage des contacts de {@code PublicProfileAssembler}.
 */
public record PublicProfileDetail(
    UUID practitionerId,
    String slug,
    String displayName,
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
    OffsetDateTime publishedAt) {}
