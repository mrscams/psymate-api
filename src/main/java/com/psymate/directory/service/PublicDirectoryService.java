package com.psymate.directory.service;

import com.psymate.directory.dto.Card;
import com.psymate.directory.dto.FacetCount;
import com.psymate.directory.dto.Facets;
import com.psymate.directory.dto.PublicProfile;
import com.psymate.directory.dto.PublicProfileAssembler;
import com.psymate.directory.dto.SearchResult;
import com.psymate.directory.model.ProfileException;
import com.psymate.directory.model.PublicDirectoryCriteria;
import com.psymate.directory.repository.PublicDirectoryRepository;
import com.psymate.directory.repository.PublicDirectoryRepository.Row;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.api.PractitionerFacade.PractitionerView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Recherche et fiche publiques. La profession et la vérification viennent d'identity en un seul
 * appel par lot. Au plus {@link PublicDirectoryRepository#MAX_CANDIDATES} profils sont traités en
 * mémoire avant pagination : total et facettes portent sur ce lot.
 */
@Service
public class PublicDirectoryService {

  static final int PAGE_SIZE = 20;
  public static final double DEFAULT_RADIUS_KM = 25;
  static final int MAX_Q = 100;
  static final int MAX_LIST = 20;
  private static final List<String> SORTS = List.of("relevance", "distance", "name");

  private final PublicDirectoryRepository repository;
  private final PractitionerFacade practitioners;

  PublicDirectoryService(PublicDirectoryRepository repository, PractitionerFacade practitioners) {
    this.repository = repository;
    this.practitioners = practitioners;
  }

  public SearchResult search(PublicDirectoryCriteria criteria, List<String> professions, int page) {
    validate(criteria, professions, page);
    List<Row> rows = repository.search(criteria);
    Map<UUID, PractitionerView> views =
        practitioners.findAll(rows.stream().map(Row::practitionerId).toList());
    List<Card> all = new ArrayList<>();
    for (Row r : rows) {
      PractitionerView view = views.get(r.practitionerId());
      if (view == null) {
        continue; // praticien introuvable : jamais listé
      }
      if (!professions.isEmpty() && !professions.contains(view.profession())) {
        continue;
      }
      all.add(
          new Card(
              r.slug(),
              r.displayName(),
              view.profession(),
              r.titleLabel(),
              "approved".equals(view.verificationStatus()),
              PublicProfileAssembler.photoUrl(r.photoAssetKey()),
              PublicProfileAssembler.excerpt(r.bioExcerpt()),
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
    int from = (int) Math.min((long) (page - 1) * PAGE_SIZE, all.size());
    List<Card> items = all.subList(from, Math.min(from + PAGE_SIZE, all.size()));
    int totalPages = (all.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    return new SearchResult(items, page, PAGE_SIZE, all.size(), totalPages, facets);
  }

  public PublicProfile profile(String slug) {
    var detail = repository.findPublished(slug).orElseThrow(PublicDirectoryService::notFound);
    PractitionerView view =
        practitioners.findAll(List.of(detail.practitionerId())).get(detail.practitionerId());
    if (view == null) {
      throw notFound();
    }
    String number =
        detail.showProfessionalNumber()
            ? practitioners.professionalNumber(detail.practitionerId()).orElse(null)
            : null;
    return PublicProfileAssembler.toPublic(
        detail, view.profession(), "approved".equals(view.verificationStatus()), number);
  }

  private static ProfileException notFound() {
    return ProfileException.of(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profil introuvable.");
  }

  private static void validate(PublicDirectoryCriteria c, List<String> professions, int page) {
    List<ProfileException.FieldError> errors = new ArrayList<>();
    if (!SORTS.contains(c.sort())) {
      errors.add(new ProfileException.FieldError("sort", "Tri inconnu."));
    }
    if (c.q() != null && c.q().length() > MAX_Q) {
      errors.add(new ProfileException.FieldError("q", "100 caractères au maximum."));
    }
    if (c.city() != null && c.city().length() > MAX_Q) {
      errors.add(new ProfileException.FieldError("city", "100 caractères au maximum."));
    }
    if ((c.lat() == null) != (c.lng() == null)) {
      errors.add(new ProfileException.FieldError("lat", "lat et lng vont ensemble."));
    }
    if (c.lat() != null && !(c.lat() >= -90 && c.lat() <= 90)) {
      errors.add(new ProfileException.FieldError("lat", "Entre -90 et 90."));
    }
    if (c.lng() != null && !(c.lng() >= -180 && c.lng() <= 180)) {
      errors.add(new ProfileException.FieldError("lng", "Entre -180 et 180."));
    }
    if (!(c.radiusKm() > 0 && c.radiusKm() <= 1000)) {
      errors.add(new ProfileException.FieldError("radiusKm", "Entre 0 (exclu) et 1000 km."));
    }
    checkList(errors, "professions", professions);
    checkList(errors, "modalities", c.modalities());
    checkList(errors, "audiences", c.audiences());
    checkList(errors, "languages", c.languages());
    if (page < 1) {
      errors.add(new ProfileException.FieldError("page", "Au moins 1."));
    }
    if (!errors.isEmpty()) {
      throw new ProfileException(
          HttpStatus.BAD_REQUEST,
          "INVALID_PARAMETERS",
          "Paramètres de recherche invalides.",
          errors,
          Map.of());
    }
  }

  private static void checkList(
      List<ProfileException.FieldError> errors, String field, List<String> values) {
    if (values != null && values.size() > MAX_LIST) {
      errors.add(new ProfileException.FieldError(field, "20 valeurs au maximum."));
    }
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
