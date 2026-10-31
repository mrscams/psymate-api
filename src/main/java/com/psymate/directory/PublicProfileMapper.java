package com.psymate.directory;

import com.psymate.directory.PublicDirectoryDtos.PublicProfile;
import com.psymate.directory.PublicDirectoryRepository.Detail;

/** Règles d'affichage des contacts (A4.10) : un champ non coché n'est jamais émis. */
final class PublicProfileMapper {

  private PublicProfileMapper() {}

  static String photoUrl(String assetKey) {
    return assetKey == null || assetKey.isBlank() ? null : "/public/media/" + assetKey;
  }

  static PublicProfile toPublic(
      Detail d, String profession, boolean verified, String professionalNumber) {
    return new PublicProfile(
        d.slug(),
        d.displayName(),
        profession,
        verified,
        d.titleLabel(),
        d.showProfessionalNumber() ? professionalNumber : null,
        photoUrl(d.photoAssetKey()),
        d.bio(),
        d.specialties(),
        d.audiences(),
        d.modalities(),
        d.languages(),
        d.city(),
        d.postalCode(),
        d.country(),
        d.showPhone() ? d.contactPhone() : null,
        d.showEmail() ? d.contactEmail() : null,
        d.showAddress() ? d.address() : null,
        d.websiteUrl(),
        d.responseDelay(),
        d.fees(),
        d.availability(),
        d.nextSlotAt(),
        d.publishedAt());
  }
}
