package com.psymate.identity.preferences;

import com.psymate.identity.api.CurrentPractitioner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Préférences par compte (A10.2, fiche 14) : objet plat, merge RFC 7396. */
@Tag(
    name = "Préférences",
    description =
        "Préférences d'affichage et de saisie du praticien connecté, mémorisées par compte :"
            + " neuf clés plates, modifiées par merge-patch (RFC 7396).")
@RestController
@RequestMapping("/api/me/preferences")
class PreferencesController {

  private final PreferencesService service;

  PreferencesController(PreferencesService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Préférences du compte (valeurs stockées complétées par les défauts)",
      description =
          """
          Session praticien. Renvoie toujours les 9 clés : la valeur enregistrée si elle est \
          valide, sinon le défaut calculé selon la profession (coach : vocabulary.subjectLabel = \
          client et entryTypes.medicationVisible = false ; psychiatre : entryTypes.medicationVisible \
          = true). Les clés inconnues stockées ne sont pas renvoyées.\
          """)
  @ApiResponses(
      @ApiResponse(
          responseCode = "200",
          description = "Les 9 préférences effectives du compte.",
          content =
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = PreferencesSchema.class))))
  Map<String, Object> getPreferences(@AuthenticationPrincipal CurrentPractitioner me) {
    return service.get(me.id());
  }

  @PatchMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, "application/merge-patch+json"})
  @Operation(
      summary = "Modifie les préférences (merge-patch RFC 7396, null = retour au défaut)",
      description =
          """
          Session praticien. Corps JSON plat (application/json ou application/merge-patch+json) \
          avec seulement les clés à changer : les autres sont conservées. null remet la clé à son \
          défaut ; une clé inconnue est ignorée sans erreur. Une seule valeur hors domaine rejette \
          toute la requête (422, errors[].field = clé fautive) et rien n'est appliqué. Renvoie les \
          9 préférences effectives après fusion ; un PATCH qui ne change rien est un succès.\
          """,
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Clés à modifier ; toutes optionnelles.",
              required = true,
              content = {
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PreferencesSchema.class)),
                @Content(
                    mediaType = "application/merge-patch+json",
                    schema = @Schema(implementation = PreferencesSchema.class))
              }))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Les 9 préférences effectives après fusion.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PreferencesSchema.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} : code INVALID_VALUE, field ="
                + " la clé fautive (par exemple overview.window) ; valeurs admises dans message ;"
                + " rien n'est appliqué.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  Map<String, Object> updatePreferences(
      @AuthenticationPrincipal CurrentPractitioner me, @RequestBody Map<String, Object> patch) {
    return service.patch(me.id(), patch);
  }
}
