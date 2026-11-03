package com.psymate.directory.service;

import com.psymate.directory.dto.Card;
import com.psymate.directory.dto.FacetCount;
import com.psymate.directory.dto.Facets;
import com.psymate.directory.dto.Item;
import com.psymate.directory.dto.PublicProfile;
import com.psymate.directory.dto.PublicProfileAssembler;
import com.psymate.directory.dto.SearchResult;
import com.psymate.directory.dto.SpecialtyFacetCount;
import com.psymate.directory.model.ProfileException;
import com.psymate.directory.model.PublicDirectoryCriteria;
import com.psymate.directory.repository.PublicDirectoryRepository;
import com.psymate.directory.repository.PublicDirectoryRepository.Row;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.api.PractitionerFacade.PractitionerView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  private final DirectoryVocabularyService vocabulary;

  PublicDirectoryService(
      PublicDirectoryRepository repository,
      PractitionerFacade practitioners,
      DirectoryVocabularyService vocabulary) {
    this.repository = repository;
    this.practitioners = practitioners;
    this.vocabulary = vocabulary;
  }

  public SearchResult search(PublicDirectoryCriteria criteria, List<String> professions, int page) {
    validate(criteria, professions, page);
    List<Item> specialtyList = vocabulary.activeItems("specialty");
    checkSpecialtiesInList(criteria.specialties(), specialtyList);
    // Sans filtre de spécialité, le lot des résultats sert aussi de base à la facette ; avec un
    // filtre, la base de la facette (mêmes critères, sans spécialité) demande une seconde lecture.
    List<Row> baseRows =
        repository.search(criteria.hasSpecialties() ? criteria.withoutSpecialties() : criteria);
    List<Row> rows = criteria.hasSpecialties() ? repository.search(criteria) : baseRows;
    Map<UUID, PractitionerView> views =
        practitioners.findAll(
            Stream.concat(baseRows.stream(), rows.stream())
                .map(Row::practitionerId)
                .distinct()
                .toList());
    List<Card> all = cards(rows, views, professions);
    List<Card> facetBase = rows == baseRows ? all : cards(baseRows, views, professions);
    Facets facets =
        new Facets(
            facet(all, c -> c.profession() == null ? List.of() : List.of(c.profession())),
            specialtyFacet(facetBase, specialtyList),
            facet(all, Card::modalities),
            facet(all, Card::audiences),
            facet(all, Card::languages));
    int from = (int) Math.min((long) (page - 1) * PAGE_SIZE, all.size());
    List<Card> items = all.subList(from, Math.min(from + PAGE_SIZE, all.size()));
    int totalPages = (all.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    return new SearchResult(items, page, PAGE_SIZE, all.size(), totalPages, facets);
  }

  /** Fiches dont le praticien est connu et dont la profession passe le filtre, ordre conservé. */
  private static List<Card> cards(
      List<Row> rows, Map<UUID, PractitionerView> views, List<String> professions) {
    List<Card> cards = new ArrayList<>();
    for (Row r : rows) {
      PractitionerView view = views.get(r.practitionerId());
      if (view == null) {
        continue; // praticien introuvable : jamais listé
      }
      if (!professions.isEmpty() && !professions.contains(view.profession())) {
        continue;
      }
      cards.add(
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
    return cards;
  }

  /** 422 si un code demandé n'est pas une spécialité active de la liste fermée (G Q15). */
  private static void checkSpecialtiesInList(List<String> requested, List<Item> active) {
    if (requested == null || requested.isEmpty()) {
      return;
    }
    Set<String> known = active.stream().map(Item::code).collect(Collectors.toSet());
    List<ProfileException.CodedFieldError> errors =
        requested.stream()
            .filter(code -> code == null || !known.contains(code))
            .distinct()
            .map(
                code ->
                    new ProfileException.CodedFieldError(
                        "specialties", "INVALID_VALUE", "Spécialité hors liste : " + code))
            .toList();
    if (!errors.isEmpty()) {
      throw ProfileException.invalidCoded(errors);
    }
  }

  /**
   * Compteur par spécialité active (une fiche compte une fois par code), du plus fréquent au moins
   * fréquent puis par ordre d'affichage de la liste ; les codes sans fiche ne sont pas rendus.
   */
  private static List<SpecialtyFacetCount> specialtyFacet(List<Card> cards, List<Item> active) {
    Map<String, Long> counts = new HashMap<>();
    for (Card c : cards) {
      for (String code : c.specialties().stream().distinct().toList()) {
        counts.merge(code, 1L, Long::sum);
      }
    }
    return active.stream()
        .filter(i -> counts.containsKey(i.code()))
        .map(i -> new SpecialtyFacetCount(i.code(), i.labelFr(), i.labelEn(), counts.get(i.code())))
        .sorted(Comparator.comparingLong(SpecialtyFacetCount::count).reversed())
        .toList();
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
    checkList(errors, "specialties", c.specialties());
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
