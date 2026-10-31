package com.psymate.directory;

import com.psymate.directory.PublicDirectoryDtos.Card;
import com.psymate.directory.PublicDirectoryDtos.FacetCount;
import com.psymate.directory.PublicDirectoryDtos.Facets;
import com.psymate.directory.PublicDirectoryDtos.PublicProfile;
import com.psymate.directory.PublicDirectoryDtos.SearchResult;
import com.psymate.directory.PublicDirectoryRepository.Criteria;
import com.psymate.directory.PublicDirectoryRepository.Row;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.api.PractitionerFacade.PractitionerView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Recherche et fiche publiques. La profession et la vérification viennent d'identity. */
@Service
class PublicDirectoryService {

  static final int PAGE_SIZE = 20;
  static final double DEFAULT_RADIUS_KM = 25;
  private static final List<String> SORTS = List.of("relevance", "distance", "name");

  private final PublicDirectoryRepository repository;
  private final PractitionerFacade practitioners;

  PublicDirectoryService(PublicDirectoryRepository repository, PractitionerFacade practitioners) {
    this.repository = repository;
    this.practitioners = practitioners;
  }

  SearchResult search(Criteria criteria, List<String> professions, int page) {
    if (!SORTS.contains(criteria.sort())) {
      throw ProfileException.of(HttpStatus.BAD_REQUEST, "INVALID_SORT", "Tri inconnu.");
    }
    if (criteria.lat() != null != (criteria.lng() != null)) {
      throw ProfileException.of(
          HttpStatus.BAD_REQUEST, "INVALID_LOCATION", "lat et lng vont ensemble.");
    }
    List<Card> all = new ArrayList<>();
    for (Row r : repository.search(criteria)) {
      Optional<PractitionerView> view = practitioners.find(r.practitionerId());
      String profession = view.map(PractitionerView::profession).orElse(null);
      if (!professions.isEmpty() && !professions.contains(profession)) {
        continue;
      }
      boolean verified = view.map(v -> "approved".equals(v.verificationStatus())).orElse(false);
      all.add(
          new Card(
              r.slug(),
              r.displayName(),
              profession,
              r.titleLabel(),
              verified,
              PublicProfileMapper.photoUrl(r.photoAssetKey()),
              r.bioExcerpt(),
              r.specialties(),
              r.audiences(),
              r.modalities(),
              r.languages(),
              r.city(),
              r.availability(),
              r.distanceKm()));
    }
    Facets facets =
        new Facets(
            facet(all, c -> c.profession() == null ? List.of() : List.of(c.profession())),
            facet(all, Card::modalities),
            facet(all, Card::audiences),
            facet(all, Card::languages));
    int safePage = Math.max(page, 1);
    int from = Math.min((safePage - 1) * PAGE_SIZE, all.size());
    List<Card> items = all.subList(from, Math.min(from + PAGE_SIZE, all.size()));
    int totalPages = (all.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    return new SearchResult(items, safePage, PAGE_SIZE, all.size(), totalPages, facets);
  }

  PublicProfile profile(String slug) {
    var detail =
        repository
            .findPublished(slug)
            .orElseThrow(
                () ->
                    ProfileException.of(
                        HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profil introuvable."));
    Optional<PractitionerView> view = practitioners.find(detail.practitionerId());
    String profession = view.map(PractitionerView::profession).orElse(null);
    boolean verified = view.map(v -> "approved".equals(v.verificationStatus())).orElse(false);
    String number =
        detail.showProfessionalNumber()
            ? practitioners.professionalNumber(detail.practitionerId()).orElse(null)
            : null;
    return PublicProfileMapper.toPublic(detail, profession, verified, number);
  }

  private static List<FacetCount> facet(List<Card> cards, Function<Card, List<String>> values) {
    Map<String, Long> counts = new TreeMap<>();
    for (Card c : cards) {
      for (String v : values.apply(c).stream().distinct().toList()) {
        counts.merge(v, 1L, Long::sum);
      }
    }
    return counts.entrySet().stream()
        .map(e -> new FacetCount(e.getKey(), e.getValue()))
        .sorted(Comparator.comparingLong(FacetCount::count).reversed())
        .toList();
  }
}
