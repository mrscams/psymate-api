package com.psymate.overview.controller;

import com.psymate.overview.dto.OverviewDto;
import com.psymate.overview.model.OverviewWindow;
import com.psymate.overview.service.OverviewService;
import com.psymate.patients.api.PatientOwnershipGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@Tag(
    name = "Vue d'ensemble",
    description =
        "Synthèse clinique d'un patient : faits clés, tendance, émotions dominantes, résumé"
            + " rédigé, séances récentes.")
class OverviewController {

  private final OverviewService service;
  private final PatientOwnershipGuard guard;
  private final OverviewMessages messages;

  OverviewController(
      OverviewService service, PatientOwnershipGuard guard, OverviewMessages messages) {
    this.service = service;
    this.guard = guard;
    this.messages = messages;
  }

  @GetMapping("/api/patients/{id}/overview")
  @Operation(
      operationId = "getPatientOverview",
      summary = "Synthèse clinique d'un patient sur une fenêtre de temps",
      description =
          """
          Session praticien propriétaire du patient (fonctionne aussi pour un patient archivé). \
          Une seule réponse porte tout : faits clés, situations actives, tendance d'intensité \
          (moyenne glissante sur 7 jours, 90 points au plus, repères de séances et de \
          médicaments), émotions dominantes comparées à la fenêtre précédente, ressentis réels, \
          brouillons, entrées depuis la dernière séance, 5 séances récentes et résumé « En bref » \
          rédigé par règles, sans IA, dans la langue d'Accept-Language (français par défaut). La \
          fenêtre window borne la tendance, les émotions et les ressentis. Les données viennent \
          d'une projection recalculée à la volée si elle est en retard sur le dossier : la \
          réponse n'est jamais obsolète ; recentActivity est lu en direct. Entrées archivées \
          exclues, entrées privées du praticien incluses.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Synthèse du patient pour la fenêtre demandée."),
    @ApiResponse(
        responseCode = "400",
        description =
            "OVERVIEW_BAD_REQUEST : paramètre invalide, errors[] {field, code, message} avec"
                + " field = window et code = INVALID_WINDOW (valeur autre que 30d, 90d, 12m ;"
                + " la casse compte). Le contrôle de propriété passe avant : un patient d'un"
                + " autre praticien reste en 403.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "PATIENT_NOT_FOUND : aucun patient avec cet identifiant.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  OverviewDto getPatientOverview(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable("id")
          UUID patientId,
      @Parameter(
              description =
                  "Fenêtre de la tendance, des émotions dominantes et des ressentis, jusqu'à"
                      + " aujourd'hui (jours UTC) : 30d = 30 jours, 90d = 90 jours (défaut), 12m ="
                      + " 12 mois (un point par semaine). Absent ou vide : 90d.",
              schema =
                  @Schema(
                      allowableValues = {"30d", "90d", "12m"},
                      defaultValue = "90d"),
              example = "90d")
          @RequestParam(defaultValue = "90d")
          String window,
      @Parameter(hidden = true) HttpServletRequest request) {
    guard.requireOwned(patientId);
    OverviewWindow parsed =
        OverviewWindow.parse(window)
            .orElseThrow(() -> new OverviewRequestException("window", "INVALID_WINDOW"));
    return service.overview(patientId, parsed, messages.localeOf(request));
  }
}
