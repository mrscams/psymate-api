package com.psymate.vocabulary;

import com.psymate.vocabulary.api.TagView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tags du praticien courant. Sera protégé par la sécurité de M0-04 (pas encore présente). */
@Tag(
    name = "Tags",
    description =
        "Tags libres du praticien, partagés entre ses patients et ses entrées : liste, création,"
            + " renommage, fusion.")
@RestController
@RequestMapping("/api/tags")
class TagController {

  @Schema(description = "Libellé d'un tag à créer ou à substituer au libellé actuel.")
  record TagRequest(
      @Schema(
              description =
                  "Libellé du tag ; obligatoire, non vide. Un « # » est ignoré, les espaces de bord"
                      + " sont retirés ; l'unicité se juge sans casse ni accents. Un libellé égal à"
                      + " une émotion visible du praticien est refusé (TAG_IS_EMOTION).",
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "Travail")
          String label) {}

  @Schema(description = "Cible d'une fusion de tags.")
  record MergeTagRequest(
      @Schema(
              description =
                  "Identifiant du tag actif du praticien qui absorbe le tag source ; obligatoire,"
                      + " différent de la source.",
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID targetId) {}

  private final TagService tags;

  TagController(TagService tags) {
    this.tags = tags;
  }

  @GetMapping
  @Operation(
      summary = "Liste les tags actifs du praticien",
      description =
          """
          Session praticien. Renvoie uniquement ses propres tags actifs (les tags fusionnés dans un \
          autre sont absents), triés par libellé normalisé. Liste complète, non paginée.""")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Tags actifs du praticien.")})
  List<TagView> list() {
    return tags.tagsOfCurrentPractitioner();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Crée un tag pour le praticien",
      description =
          """
          Session praticien. Le tag lui appartient. Un libellé équivalent à un tag actif existant \
          (sans casse, accents ni « # ») est refusé ; un libellé qui correspond à une émotion \
          globale ou à une de ses émotions personnalisées aussi, avec la propriété suggestedEmotion \
          (émotion à utiliser à la place). Le libellé d'un tag fusionné est de nouveau libre.""")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Tag créé ; corps = tag."),
    @ApiResponse(
        responseCode = "409",
        description = "TAG_EXISTS : un tag actif porte déjà ce libellé.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : errors[] {field, message} avec field = label (obligatoire) ;"
                + " TAG_IS_EMOTION : le libellé est déjà une émotion, errors[] avec field = label"
                + " et propriété suggestedEmotion (objet EmotionView).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  TagView create(@RequestBody TagRequest request) {
    return tags.create(request.label());
  }

  @PatchMapping("/{id}")
  @Operation(
      summary = "Renomme un tag du praticien",
      description =
          """
          Session praticien propriétaire du tag ; un tag déjà fusionné est introuvable. Mêmes \
          règles de libellé qu'à la création ; le nouveau libellé peut différer du précédent \
          seulement par la casse ou les accents. Le renommage s'applique partout où le tag est \
          utilisé (patients, entrées).""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tag renommé ; corps = tag."),
    @ApiResponse(
        responseCode = "404",
        description =
            "TAG_NOT_FOUND : tag inexistant, archivé (fusionné) ou appartenant à un autre"
                + " praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "TAG_EXISTS : un autre tag actif porte déjà ce libellé.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : errors[] {field, message} avec field = label (obligatoire) ;"
                + " TAG_IS_EMOTION : le libellé est déjà une émotion, avec propriété"
                + " suggestedEmotion.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  TagView rename(
      @Parameter(
              description = "Identifiant du tag.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @RequestBody TagRequest request) {
    return tags.rename(id, request.label());
  }

  @PostMapping("/{id}/merge")
  @Operation(
      summary = "Fusionne un tag dans un autre",
      description =
          """
          Session praticien propriétaire des deux tags, tous deux actifs. Le tag source (id du \
          chemin) est archivé et son libellé redevient libre ; ses patients et ses entrées sont \
          rattachés au tag cible sans doublon, et son compteur d'usages s'ajoute à celui de la \
          cible. Réponse 200 avec le tag cible mis à jour.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Fusion faite ; corps = tag cible."),
    @ApiResponse(
        responseCode = "404",
        description =
            "TAG_NOT_FOUND : tag source ou tag cible inexistant, archivé (fusionné) ou appartenant"
                + " à un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : errors[] {field, message} avec field = targetId (absent, ou"
                + " identique à la source).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  TagView merge(
      @Parameter(
              description = "Identifiant du tag source, celui qui est absorbé puis archivé.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @RequestBody MergeTagRequest request) {
    return tags.merge(id, request.targetId());
  }
}
