package com.psymate.directory;

import com.psymate.directory.PublicDirectoryDtos.PublicProfile;
import com.psymate.directory.PublicDirectoryDtos.SearchResult;
import com.psymate.directory.PublicDirectoryRepository.Criteria;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Annuaire public : sans authentification, lecture seule, cache public 60 s. */
@RestController
@RequestMapping("/api/public/directory")
class PublicDirectoryController {

  private static final CacheControl CACHE =
      CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

  private final PublicDirectoryService service;

  PublicDirectoryController(PublicDirectoryService service) {
    this.service = service;
  }

  @GetMapping
  ResponseEntity<SearchResult> search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lng,
      @RequestParam(required = false) Double radiusKm,
      @RequestParam(required = false) List<String> professions,
      @RequestParam(required = false) List<String> modalities,
      @RequestParam(required = false) List<String> audiences,
      @RequestParam(required = false) List<String> languages,
      @RequestParam(defaultValue = "false") boolean accepting,
      @RequestParam(defaultValue = "relevance") String sort,
      @RequestParam(defaultValue = "1") int page) {
    Criteria criteria =
        new Criteria(
            q,
            city,
            lat,
            lng,
            radiusKm == null ? PublicDirectoryService.DEFAULT_RADIUS_KM : radiusKm,
            modalities,
            audiences,
            languages,
            accepting,
            sort);
    SearchResult result =
        service.search(criteria, professions == null ? List.of() : professions, page);
    return ResponseEntity.ok().cacheControl(CACHE).body(result);
  }

  @GetMapping("/practitioners/{slug}")
  ResponseEntity<PublicProfile> profile(@PathVariable String slug) {
    return ResponseEntity.ok().cacheControl(CACHE).body(service.profile(slug));
  }
}
