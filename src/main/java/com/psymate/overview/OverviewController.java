package com.psymate.overview;

import com.psymate.overview.OverviewDtos.OverviewDto;
import com.psymate.patients.api.PatientOwnershipGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vue d'ensemble d'un patient (A6, écran 2) : lecture de la projection {@code patient_overview}.
 */
@RestController
@Tag(name = "overview")
class OverviewController {

  private final OverviewService service;
  private final PatientOwnershipGuard guard;

  OverviewController(OverviewService service, PatientOwnershipGuard guard) {
    this.service = service;
    this.guard = guard;
  }

  @GetMapping("/api/patients/{id}/overview")
  @Operation(
      operationId = "getPatientOverview",
      summary = "Vue d'ensemble d'un patient",
      description =
          "Lit la projection patient_overview : faits clés, situations actives, tendance"
              + " d'intensité (moyenne glissante sur 7 jours, 90 points au plus, repères de"
              + " séances et de médicaments), émotions dominantes avec variation sur la fenêtre"
              + " précédente, brouillons et séance ouverte. La fenêtre (window) borne la tendance"
              + " et les émotions. Si la projection est en retard sur patient.clinical_rev, elle"
              + " est recalculée et réécrite avant de répondre : la réponse n'est jamais"
              + " obsolète. recentActivity (10 dernières entrées non archivées) est toujours lu"
              + " en direct. Les entrées archivées sont exclues, les entrées privées du"
              + " praticien incluses.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Vue d'ensemble pour la fenêtre demandée"),
    @ApiResponse(
        responseCode = "400",
        description = "Fenêtre invalide (errors[] par champ, code INVALID_WINDOW)",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Le patient n'appartient pas au praticien courant",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Patient inconnu",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  OverviewDto getPatientOverview(
      @Parameter(description = "Identifiant du patient") @PathVariable("id") UUID patientId,
      @Parameter(
              description = "Fenêtre de la tendance et des émotions : 30d, 90d (défaut) ou 12m",
              schema =
                  @Schema(
                      allowableValues = {"30d", "90d", "12m"},
                      defaultValue = "90d"))
          @RequestParam(defaultValue = "90d")
          String window) {
    guard.requireOwned(patientId);
    OverviewWindow parsed =
        OverviewWindow.parse(window)
            .orElseThrow(() -> new OverviewRequestException("window", "INVALID_WINDOW"));
    return service.overview(patientId, parsed);
  }
}
