package com.psymate.billing.controller;

import com.psymate.billing.dto.BillableSessionDto;
import com.psymate.billing.dto.BillableSessionsDto;
import com.psymate.billing.dto.DismissedSessionsDto;
import com.psymate.billing.dto.UpdateBillableSessionRequest;
import com.psymate.billing.service.BillableSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Séances à facturer (M15-07, L5.2, fiche 34) : liste par patient, séances écartées, « ne pas
 * facturer », « remettre à facturer », tarif d'une séance. Montants en centimes d'euro entiers (G
 * Q84) ; tarif selon G Q87 ; création à la demande selon G Q86.
 */
@Tag(
    name = "Facturation : séances à facturer",
    description =
        "Séances closes (de type session, ni accueil ni contact) qui n'ont pas encore de facture."
            + " Montants en centimes d'euro entiers (6000 = 60,00 €). Aucun texte clinique : date,"
            + " modalité, durée, prestation et tarif.")
@RestController
class BillableSessionController {

  private static final String PROBLEM = "application/problem+json";
  private static final String ID_EXAMPLE = "3f8e2b1a-6c4d-4e57-9a0b-1d2c3e4f5a6b";
  private static final String NOT_BILLABLE =
      "SESSION_NOT_BILLABLE : séance inexistante, ouverte, archivée, d'accueil, de contact, ou"
          + " d'un autre praticien.";
  private static final String ALREADY_INVOICED =
      "SESSION_ALREADY_INVOICED : la séance est dans une facture, brouillon compris.";
  private static final String MALFORMED =
      "MALFORMED_REQUEST : identifiant qui n'est pas un UUID, ou corps JSON illisible.";
  private static final String ON_DEMAND =
      " Si la séance est close mais pas encore connue de la facturation (événement de clôture"
          + " pas encore traité), sa ligne est créée à la demande.";

  private final BillableSessionService sessions;

  BillableSessionController(BillableSessionService sessions) {
    this.sessions = sessions;
  }

  @GetMapping("/api/billing/billable-sessions")
  @Operation(
      summary = "Séances à facturer, par patient",
      description =
          """
          Session praticien. Séances à l'état pending du praticien courant : un groupe par \
          patient, par nom affiché (sans tenir compte de la casse ni des accents), séances par \
          date croissante. Tarif de chaque séance (G Q87) : montant fixé sur la séance, sinon \
          tarif du patient, sinon prix de la prestation retenue (celle de la séance, sinon la \
          prestation par défaut) ; rateSource dit lequel. dismissedCount compte les séances \
          écartées des 90 derniers jours (du patient demandé s'il y en a un). Un patient \
          archivé garde son groupe (patient.archived).\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Séances à facturer."),
    @ApiResponse(
        responseCode = "404",
        description = "PATIENT_NOT_FOUND : patientId inexistant ou d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : until INVALID_FORMAT (pas une date AAAA-MM-JJ) ;"
                + " patientId INVALID_FORMAT (pas un UUID).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  BillableSessionsDto listBillableSessions(
      @Parameter(
              description =
                  "Jour limite inclus (AAAA-MM-JJ) : séances du jour ou d'avant. Absent : sans"
                      + " limite.",
              example = "2026-11-30")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate until,
      @Parameter(description = "Un seul patient. Absent : tous.", example = ID_EXAMPLE)
          @RequestParam(required = false)
          UUID patientId) {
    return sessions.list(until, patientId);
  }

  @GetMapping("/api/billing/billable-sessions/dismissed")
  @Operation(
      summary = "Séances écartées des 90 derniers jours",
      description =
          """
          Session praticien. Séances marquées « ne pas facturer » (y compris celles écartées \
          par l'archivage de la séance) dont le jour tombe dans les 90 derniers jours (au jour \
          de Europe/Paris), la plus récente d'abord, chacune avec son patient. Mêmes champs \
          qu'une séance à facturer, sans regroupement.\
          """)
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Séances écartées.")})
  DismissedSessionsDto listDismissedSessions() {
    return sessions.dismissed();
  }

  @PostMapping("/api/billing/billable-sessions/{sessionEntryId}/dismiss")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Ne pas facturer une séance",
      description =
          "Session praticien. La séance passe à l'état dismissed et quitte la liste des séances à"
              + " facturer ; réversible par /restore. Idempotent : une séance déjà écartée répond"
              + " 204."
              + ON_DEMAND)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Séance écartée."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_BILLABLE,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = ALREADY_INVOICED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  void dismissBillableSession(
      @Parameter(description = "Identifiant de la séance.", example = ID_EXAMPLE) @PathVariable
          UUID sessionEntryId) {
    sessions.dismiss(sessionEntryId);
  }

  @PostMapping("/api/billing/billable-sessions/{sessionEntryId}/restore")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Remettre une séance à facturer",
      description =
          "Session praticien. La séance écartée revient à l'état pending. Idempotent : une séance"
              + " déjà à facturer répond 204. Une séance écartée dont la séance a été archivée"
              + " depuis n'est pas remise (404)."
              + ON_DEMAND)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Séance remise à facturer."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_BILLABLE,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = ALREADY_INVOICED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  void restoreBillableSession(
      @Parameter(description = "Identifiant de la séance.", example = ID_EXAMPLE) @PathVariable
          UUID sessionEntryId) {
    sessions.restore(sessionEntryId);
  }

  @PatchMapping("/api/billing/billable-sessions/{sessionEntryId}")
  @Operation(
      summary = "Change le tarif ou la prestation d'une séance",
      description =
          "Session praticien. Fusion partielle de amount et serviceId : un champ absent reste"
              + " inchangé, null l'efface et rend le tarif calculé (tarif du patient, sinon prix"
              + " de la prestation retenue). amount est un entier en centimes d'euro strictement"
              + " positif : un nombre décimal ou une chaîne est refusé (INVALID_FORMAT). serviceId"
              + " désigne une prestation non archivée du praticien. Tout autre champ est refusé"
              + " (UNKNOWN_FIELD). Valable aussi pour une séance écartée. Répond la séance avec"
              + " son tarif retenu."
              + ON_DEMAND)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Séance modifiée."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_BILLABLE,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = ALREADY_INVOICED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : amount OUT_OF_RANGE (zéro, négatif ou au-delà d'un"
                + " entier 32 bits) ou INVALID_FORMAT (nombre décimal, chaîne) ; serviceId"
                + " INVALID_VALUE (prestation archivée, inexistante ou d'un autre praticien) ou"
                + " INVALID_FORMAT (pas un UUID) ; UNKNOWN_FIELD (champ refusé).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  BillableSessionDto updateBillableSession(
      @Parameter(description = "Identifiant de la séance.", example = ID_EXAMPLE) @PathVariable
          UUID sessionEntryId,
      @Valid @RequestBody UpdateBillableSessionRequest request) {
    return sessions.update(sessionEntryId, request);
  }
}
