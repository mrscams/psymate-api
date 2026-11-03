package com.psymate.directory.controller;

import com.psymate.directory.dto.DirectoryVocabularyDto;
import com.psymate.directory.dto.PublicProfile;
import com.psymate.directory.dto.SearchResult;
import com.psymate.directory.model.PublicDirectoryCriteria;
import com.psymate.directory.service.DirectoryVocabularyService;
import com.psymate.directory.service.PublicDirectoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Annuaire public : sans authentification, lecture seule, cache public 60 s. */
@Tag(
    name = "Annuaire public",
    description =
        "Annuaire public des praticiens publiés, sans authentification ni donnée patient :"
            + " recherche filtrée et fiche d'un praticien.")
@RestController
@RequestMapping("/api/public/directory")
class PublicDirectoryController {

  private static final CacheControl CACHE =
      CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

  private final PublicDirectoryService service;
  private final DirectoryVocabularyService vocabulary;

  PublicDirectoryController(PublicDirectoryService service, DirectoryVocabularyService vocabulary) {
    this.service = service;
    this.vocabulary = vocabulary;
  }

  @GetMapping("/vocabulary")
  @Operation(
      summary = "Liste les valeurs de l'annuaire public (libellés et codes des filtres)",
      description =
          """
          Public, sans session ; limité à 60 requêtes par minute et par IP (429 RATE_LIMITED). \
          Même contenu que GET /api/vocabulary/directory (réservé aux praticiens) : spécialités \
          et publics actifs, modalités et langues, avec leurs libellés français et anglais ; les \
          codes sont ceux des filtres de GET /api/public/directory. Listes complètes, non \
          paginées. Aucune donnée de praticien ni de patient. 200 avec Cache-Control: \
          max-age=60, public.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Listes fermées de l'annuaire.",
        headers =
            @Header(
                name = "Cache-Control",
                description = "max-age=60, public.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "429",
        description = "RATE_LIMITED : au-delà de 60 requêtes par minute et par IP.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<DirectoryVocabularyDto> getPublicDirectoryVocabulary() {
    return ResponseEntity.ok().cacheControl(CACHE).body(vocabulary.directoryVocabulary());
  }

  @GetMapping
  @Operation(
      summary = "Recherche les praticiens publiés de l'annuaire",
      description =
          """
          Public, sans session ; limité à 60 requêtes par minute et par IP (429 RATE_LIMITED). \
          Ne rend que les profils publiés. Les filtres se cumulent ; pour modalities, audiences \
          et languages une fiche est retenue si elle a au moins une des valeurs demandées. \
          Réponse paginée par page de 20 (page, totalPages) ; au plus 500 fiches sont examinées \
          avant pagination, total et facettes portent sur ce lot. 200 avec Cache-Control: \
          max-age=60, public.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Page de résultats avec facettes.",
        headers =
            @Header(
                name = "Cache-Control",
                description = "max-age=60, public.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description =
            "INVALID_PARAMETERS : errors[] {field, message} (pas de code par champ) ; champs :"
                + " sort (hors relevance, distance, name), q et city (plus de 100 caractères), lat"
                + " et lng (l'un sans l'autre, ou hors de -90 à 90 et -180 à 180), radiusKm (hors"
                + " de 0 exclu à 1000), professions, modalities, audiences, languages (plus de 20"
                + " valeurs), page (inférieure à 1), ou tout paramètre dont la valeur n'a pas le"
                + " bon type (nombre, booléen).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<SearchResult> searchPublicDirectory(
      @Parameter(
              description =
                  "Texte libre, 100 caractères au plus : tous les mots doivent correspondre, dans"
                      + " le nom, les codes de spécialités (ex. anxiety), la ville ou la"
                      + " présentation, sans tenir compte des accents ; un nom proche est aussi"
                      + " retenu. Absent : pas de filtre texte.",
              example = "Lyon")
          @RequestParam(required = false)
          String q,
      @Parameter(
              description =
                  "Ville exacte, 100 caractères au plus, sans tenir compte de la casse ni des"
                      + " accents. Ignoré quand lat et lng sont fournis.",
              example = "Lyon")
          @RequestParam(required = false)
          String city,
      @Parameter(
              description =
                  "Latitude du point de recherche en degrés, de -90 à 90 ; va avec lng. Ne rend"
                      + " que les fiches dont la position est renseignée, dans radiusKm, avec"
                      + " distanceKm. En V1 aucune fiche n'a encore de position : un filtre"
                      + " lat/lng ne renvoie aucun résultat.",
              example = "45.76")
          @RequestParam(required = false)
          Double lat,
      @Parameter(
              description =
                  "Longitude du point de recherche en degrés, de -180 à 180 ; va avec lat.",
              example = "4.84")
          @RequestParam(required = false)
          Double lng,
      @Parameter(
              description =
                  "Rayon de recherche en kilomètres autour de lat et lng, de 0 (exclu) à 1000."
                      + " Défaut : 25. Sans effet quand lat et lng sont absents.",
              example = "25")
          @RequestParam(required = false)
          Double radiusKm,
      @Parameter(
              description =
                  "Professions retenues parmi psychologist, psychiatrist, coach : valeurs séparées"
                      + " par des virgules ou paramètre répété, 20 au plus. Absent : toutes.",
              example = "psychologist,coach")
          @RequestParam(required = false)
          List<String> professions,
      @Parameter(
              description =
                  "Modalités retenues parmi in_person, video, home : valeurs séparées par des"
                      + " virgules ou paramètre répété, 20 au plus. Absent : toutes.",
              example = "video")
          @RequestParam(required = false)
          List<String> modalities,
      @Parameter(
              description =
                  "Publics accueillis retenus (codes de GET /api/vocabulary/directory) : valeurs"
                      + " séparées par des virgules ou paramètre répété, 20 au plus. Absent :"
                      + " tous.",
              example = "adults,adolescents")
          @RequestParam(required = false)
          List<String> audiences,
      @Parameter(
              description =
                  "Langues retenues, codes ISO 639-1 : valeurs séparées par des virgules ou"
                      + " paramètre répété, 20 au plus. Absent : toutes.",
              example = "fr,en")
          @RequestParam(required = false)
          List<String> languages,
      @Parameter(
              description =
                  "true pour ne garder que les praticiens qui acceptent de nouveaux patients"
                      + " (availability = accepting). Défaut : false.",
              example = "false")
          @RequestParam(defaultValue = "false")
          boolean accepting,
      @Parameter(
              description =
                  "Tri : relevance (pertinence du texte q, sinon publications les plus récentes),"
                      + " distance (plus proches d'abord, exige lat et lng, sinon ordre"
                      + " alphabétique) ou name (ordre alphabétique du nom affiché). Défaut :"
                      + " relevance.",
              schema = @Schema(allowableValues = {"relevance", "distance", "name"}),
              example = "relevance")
          @RequestParam(defaultValue = "relevance")
          String sort,
      @Parameter(
              description = "Numéro de page, à partir de 1 ; 20 résultats par page. Défaut : 1.",
              example = "1")
          @RequestParam(defaultValue = "1")
          int page) {
    PublicDirectoryCriteria criteria =
        new PublicDirectoryCriteria(
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
  @Operation(
      summary = "Rend la fiche publique d'un praticien publié",
      description =
          """
          Public, sans session ; limité à 60 requêtes par minute et par IP (429 RATE_LIMITED). \
          Ne rend que les profils publiés. Téléphone, e-mail et adresse n'apparaissent que si le \
          praticien a coché l'interrupteur correspondant, le numéro professionnel que s'il \
          l'autorise ; les champs masqués sont null. 200 avec Cache-Control: \
          max-age=60, public.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Fiche publique du praticien.",
        headers =
            @Header(
                name = "Cache-Control",
                description = "max-age=60, public.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "404",
        description = "PROFILE_NOT_FOUND : aucun profil publié avec cette adresse.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<PublicProfile> getPublicPractitionerProfile(
      @Parameter(
              description = "Adresse publique (slug) du praticien, rendue par la recherche.",
              example = "claire-lambert")
          @PathVariable
          String slug) {
    return ResponseEntity.ok().cacheControl(CACHE).body(service.profile(slug));
  }
}
