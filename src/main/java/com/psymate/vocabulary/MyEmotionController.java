package com.psymate.vocabulary;

import com.psymate.vocabulary.EmotionService.MergeView;
import com.psymate.vocabulary.api.EmotionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Émotions personnalisées du praticien courant. Sera protégé par la sécurité de M0-04. */
@Tag(
    name = "Émotions personnelles",
    description =
        "Émotions personnalisées du praticien : création, renommage, fusion réversible pendant 30"
            + " jours.")
@RestController
@RequestMapping("/api/me/emotions")
class MyEmotionController {

  @Schema(description = "Création d'une émotion personnalisée.")
  record CreateEmotionRequest(
      @Schema(
              description =
                  "Libellé français ; obligatoire, non vide. Doit être distinct, sans casse ni"
                      + " accents, de tout libellé d'émotion globale et de ses autres émotions"
                      + " personnalisées (409 EMOTION_LABEL_EXISTS sinon).",
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "Lassitude")
          String labelFr,
      @Schema(
              description = "Famille de la roue de Plutchik où ranger l'émotion ; obligatoire.",
              requiredMode = Schema.RequiredMode.REQUIRED,
              allowableValues = {
                "joy",
                "trust",
                "fear",
                "surprise",
                "sadness",
                "disgust",
                "anger",
                "anticipation"
              },
              example = "sadness")
          String family) {}

  @Schema(description = "Nouveau libellé d'une émotion personnalisée.")
  record RenameEmotionRequest(
      @Schema(
              description =
                  "Nouveau libellé français ; obligatoire, non vide, mêmes règles d'unicité qu'à la"
                      + " création.",
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "Fatigue morale")
          String labelFr) {}

  @Schema(description = "Cible d'une fusion d'émotions.")
  record MergeEmotionRequest(
      @Schema(
              description =
                  "Code de l'émotion active qui absorbe la source : une émotion globale ou une autre"
                      + " émotion personnalisée du praticien. Obligatoire, différent de la source.",
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "sadness")
          String targetCode) {}

  private final EmotionService emotions;

  MyEmotionController(EmotionService emotions) {
    this.emotions = emotions;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Crée une émotion personnalisée",
      description =
          """
          Session praticien. L'émotion lui appartient et reste invisible aux autres praticiens ; \
          son code est généré (« c_ » plus 8 caractères hexadécimaux) et son niveau d'intensité est \
          non renseigné. Elle devient aussitôt disponible dans GET /api/vocabulary/emotions. \
          Réponse 201 avec l'émotion.""")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Émotion créée ; corps = émotion."),
    @ApiResponse(
        responseCode = "409",
        description =
            "EMOTION_LABEL_EXISTS : le libellé est déjà pris par une émotion globale ou par une"
                + " émotion personnalisée du praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : errors[] {field, message} avec field = labelFr (obligatoire) ou"
                + " family (obligatoire ou famille inconnue).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  EmotionView create(@RequestBody CreateEmotionRequest request) {
    return emotions.create(request.labelFr(), request.family());
  }

  @PatchMapping("/{code}")
  @Operation(
      summary = "Renomme une émotion personnalisée",
      description =
          """
          Session praticien propriétaire de l'émotion. Seules ses émotions personnalisées actives \
          sont modifiables : une émotion globale ou celle d'un autre praticien est introuvable. \
          Seul le libellé français change ; le code reste stable. Réponse 200 avec l'émotion.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Émotion renommée ; corps = émotion."),
    @ApiResponse(
        responseCode = "404",
        description =
            "EMOTION_NOT_FOUND : code inconnu, émotion globale ou émotion d'un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "EMOTION_INACTIVE : l'émotion a été fusionnée ; EMOTION_LABEL_EXISTS : le libellé est"
                + " déjà pris par une autre émotion.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : errors[] {field, message} avec field = labelFr (obligatoire).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  EmotionView rename(
      @Parameter(
              description =
                  "Code de l'émotion personnalisée (c_ suivi de 8 caractères hexadécimaux).",
              example = "c_1a2b3c4d")
          @PathVariable
          String code,
      @RequestBody RenameEmotionRequest request) {
    return emotions.rename(code, request.labelFr());
  }

  @PostMapping("/{code}/merge")
  @Operation(
      summary = "Fusionne une émotion personnalisée dans une autre émotion",
      description =
          """
          Session praticien propriétaire de l'émotion source (personnalisée, active). Tous les \
          ressentis qui la portent passent à l'émotion cible (le code d'origine est conservé pour \
          l'annulation), la source devient inactive et disparaît de la liste des émotions. La \
          fusion est annulable pendant 30 jours via POST /api/me/emotions/merges/{id}/undo (champ \
          undoUntil). Réponse 200 avec la fusion.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Fusion faite ; corps = fusion annulable."),
    @ApiResponse(
        responseCode = "404",
        description =
            "EMOTION_NOT_FOUND : émotion source inconnue, globale ou d'un autre praticien, ou"
                + " émotion cible introuvable (inactive ou d'un autre praticien).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "EMOTION_INACTIVE : l'émotion source a déjà été fusionnée.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : errors[] {field, message} avec field = targetCode (absent, ou"
                + " identique à la source).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  MergeView merge(
      @Parameter(
              description = "Code de l'émotion personnalisée source, celle qui est absorbée.",
              example = "c_1a2b3c4d")
          @PathVariable
          String code,
      @RequestBody MergeEmotionRequest request) {
    return emotions.merge(code, request.targetCode());
  }

  @PostMapping("/merges/{id}/undo")
  @Operation(
      summary = "Annule une fusion d'émotions dans les 30 jours",
      description =
          """
          Session praticien propriétaire de la fusion. Réactive l'émotion source et lui rend les \
          ressentis réécrits par la fusion. Possible une seule fois et jusqu'à undoUntil (30 jours \
          après la fusion). Aucun corps de requête. Réponse 200 avec la fusion, undoneAt \
          renseigné.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Fusion annulée ; corps = fusion avec undoneAt."),
    @ApiResponse(
        responseCode = "404",
        description = "MERGE_NOT_FOUND : fusion inconnue ou appartenant à un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "MERGE_ALREADY_UNDONE : la fusion a déjà été annulée ; MERGE_UNDO_EXPIRED : le délai"
                + " d'annulation de 30 jours est dépassé.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  MergeView undo(
      @Parameter(
              description = "Identifiant de la fusion, lu dans le champ id de la réponse du merge.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id) {
    return emotions.undo(id);
  }
}
