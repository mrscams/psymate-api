package com.psymate.directory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** DTO de l'annuaire public (aucune donnée patient, contacts filtrés). */
final class PublicDirectoryDtos {

  private PublicDirectoryDtos() {}

  record Card(
      String slug,
      String displayName,
      String profession,
      String titleLabel,
      boolean verified,
      String photoUrl,
      String bioExcerpt,
      List<String> specialties,
      List<String> audiences,
      List<String> modalities,
      List<String> languages,
      String city,
      String availability,
      Double distanceKm) {}

  record FacetCount(String code, long count) {}

  record Facets(
      List<FacetCount> professions,
      List<FacetCount> modalities,
      List<FacetCount> audiences,
      List<FacetCount> languages) {}

  record SearchResult(
      List<Card> items, int page, int pageSize, long total, int totalPages, Facets facets) {}

  record PublicProfile(
      String slug,
      String displayName,
      String profession,
      boolean verified,
      String titleLabel,
      String professionalNumber,
      String photoUrl,
      String bio,
      List<String> specialties,
      List<String> audiences,
      List<String> modalities,
      List<String> languages,
      String city,
      String postalCode,
      String country,
      String phone,
      String email,
      String address,
      String websiteUrl,
      String responseDelay,
      String fees,
      String availability,
      LocalDate nextSlotAt,
      OffsetDateTime publishedAt) {}
}
