package com.psymate.directory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Passage de l'entité au DTO. Les entités ne sortent jamais de la couche application : ProfileDto
 * ne les connaît pas (garde ArchUnit).
 */
final class ProfileAssembler {

  private ProfileAssembler() {}

  static ProfileDto toDto(PractitionerProfile p, String profession) {
    return new ProfileDto(
        p.getSlug(),
        p.isPublished(),
        p.getDisplayName(),
        profession,
        p.getTitleLabel(),
        p.isShowProfessionalNumber(),
        p.getPhotoAssetKey(),
        p.getBio(),
        List.copyOf(p.getSpecialties()),
        List.copyOf(p.getAudiences()),
        List.copyOf(p.getModalities()),
        List.copyOf(p.getLanguages()),
        p.getCity(),
        p.getPostalCode(),
        p.getCountry(),
        p.getContactPhone(),
        p.getContactEmail(),
        p.getWebsiteUrl(),
        p.getAddress(),
        p.isShowPhone(),
        p.isShowEmail(),
        p.isShowAddress(),
        p.getResponseDelay(),
        p.getFees(),
        p.getAvailability(),
        p.getNextSlotAt(),
        utc(p.getPublishedAt()),
        utc(p.getUpdatedAt()));
  }

  private static OffsetDateTime utc(OffsetDateTime t) {
    return t == null ? null : t.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
