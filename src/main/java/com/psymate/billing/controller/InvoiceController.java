package com.psymate.billing.controller;

import com.psymate.billing.dto.CreateInvoiceBatchRequest;
import com.psymate.billing.dto.CreateInvoiceRequest;
import com.psymate.billing.dto.InvoiceBatchDto;
import com.psymate.billing.dto.InvoiceDto;
import com.psymate.billing.dto.InvoicePageDto;
import com.psymate.billing.dto.UpdateInvoiceRequest;
import com.psymate.billing.service.InvoiceListService;
import com.psymate.billing.service.InvoiceService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Factures : brouillons et liste (M15-08, L5.3 jusqu'à DELETE, fiches 35 et 36). Montants en
 * centimes d'euro entiers (G Q84) ; tarif des séances selon G Q87 ; cycle selon G Q88 (seul un
 * brouillon se modifie ou se supprime). L'émission, le numéro et le PDF viennent de M15-09.
 */
@Tag(
    name = "Facturation : factures",
    description =
        "Brouillons de facture, liste et lecture des factures. Montants en centimes d'euro"
            + " entiers (6000 = 60,00 €). Une ligne de séance ne porte aucun texte clinique : une"
            + " date et une désignation.")
@RestController
class InvoiceController {

  private static final String PROBLEM = "application/problem+json";
  private static final String ID_EXAMPLE = "3f8e2b1a-6c4d-4e57-9a0b-1d2c3e4f5a6b";
  private static final String MALFORMED =
      "MALFORMED_REQUEST : identifiant qui n'est pas un UUID, ou corps JSON illisible.";
  private static final String NOT_FOUND =
      "INVOICE_NOT_FOUND : facture inexistante ou d'un autre praticien.";
  private static final String ISSUED =
      "INVOICE_ISSUED : la facture est émise, annulée, ou c'est un avoir : elle ne se modifie"
          + " plus et ne se supprime pas.";
  private static final String SESSION_CODES =
      "SESSION_NOT_BILLABLE (404) : séance inexistante, ouverte, archivée, d'accueil, de"
          + " contact, ou d'un autre praticien. SESSION_ALREADY_INVOICED (409) : séance déjà dans"
          + " une autre facture, brouillon compris ; sessionEntryId dans le problème.";
  private static final String LINE_FIELDS =
      " lines OUT_OF_RANGE (plus de 100 lignes libres ou de 200 lignes de séance) ;"
          + " lines[i].label REQUIRED ou OUT_OF_RANGE (200 caractères) ; lines[i].quantity"
          + " OUT_OF_RANGE (1 à 999) ou INVALID_FORMAT ; lines[i].unitPrice REQUIRED, OUT_OF_RANGE"
          + " (-10000000 à 10000000) ou INVALID_FORMAT (nombre décimal, chaîne) ;"
          + " lines[i].serviceId INVALID_VALUE (prestation inconnue ou d'un autre praticien) ;"
          + " lines[i].sessionEntryId INVALID_VALUE (séance en double, ou d'un autre patient) ;"
          + " lines[i].occurredOn INVALID_FORMAT ; UNKNOWN_FIELD (champ refusé).";

  private final InvoiceService invoices;
  private final InvoiceListService list;

  InvoiceController(InvoiceService invoices, InvoiceListService list) {
    this.invoices = invoices;
    this.list = list;
  }

  @PostMapping("/api/invoices")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Crée un brouillon de facture",
      description =
          """
          Session praticien. Brouillon d'un patient du praticien : une ligne par séance de \
          sessionEntryIds, par date (désignation « {prestation retenue} du JJ/MM/AAAA » ou \
          « Séance du JJ/MM/AAAA », prix = tarif de la séance selon G Q87, quantité 1, date et \
          prestation de la séance), puis les lignes données dans leur ordre. Chaque séance \
          passe à l'état invoiced. Destinataire par défaut : le tiers payeur du profil de \
          facturation s'il existe, sinon le patient avec l'adresse de son profil. Date \
          d'émission prévue : aujourd'hui (Europe/Paris) ; échéance et mention de bas de page : \
          celles des réglages. Une séance encore inconnue de la facturation mais close est \
          créée à la demande. Répond la facture créée.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Brouillon créé."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "PATIENT_NOT_FOUND : patient inexistant ou d'un autre praticien. "
                + "SESSION_NOT_BILLABLE : voir 409.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "PATIENT_ARCHIVED : patient archivé, plus de nouveau brouillon (patientId dans le"
                + " problème). "
                + SESSION_CODES,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "RATE_REQUIRED : une séance demandée n'a aucun tarif (ni montant fixé, ni tarif du"
                + " patient, ni prestation) ; sessionEntryId dans le problème. VALIDATION_FAILED"
                + " avec errors[] : patientId REQUIRED ou INVALID_FORMAT ; sessionEntryIds"
                + " OUT_OF_RANGE (plus de 200) ou INVALID_FORMAT ; sessionEntryIds[i]"
                + " INVALID_VALUE (doublon, ou séance d'un autre patient) ;"
                + LINE_FIELDS,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  InvoiceDto createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
    return invoices.create(request);
  }

  @PostMapping("/api/invoices/batch")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Crée des brouillons par lot",
      description =
          """
          Session praticien. De 1 à 200 séances : un brouillon par patient, avec les mêmes \
          lignes de séance que la création d'un brouillon. Tout ou rien : à la première erreur, \
          aucun brouillon n'est créé et aucune séance ne change d'état. Répond les brouillons, \
          dans l'ordre où chaque patient apparaît la première fois.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Brouillons créés."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "SESSION_NOT_BILLABLE : voir 409.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "PATIENT_ARCHIVED : le patient d'une séance est archivé (patientId dans le"
                + " problème). "
                + SESSION_CODES,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "RATE_REQUIRED : une séance n'a aucun tarif ; sessionEntryId dans le problème."
                + " VALIDATION_FAILED avec errors[] : sessionEntryIds REQUIRED (vide ou absent),"
                + " OUT_OF_RANGE (plus de 200) ou INVALID_FORMAT ; sessionEntryIds[i]"
                + " INVALID_VALUE (doublon) ; UNKNOWN_FIELD.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  InvoiceBatchDto createInvoiceBatch(@Valid @RequestBody CreateInvoiceBatchRequest request) {
    return invoices.batch(request);
  }

  @GetMapping("/api/invoices")
  @Operation(
      summary = "Liste les factures",
      description =
          """
          Session praticien. Factures et avoirs du praticien : brouillons d'abord, puis date \
          d'émission décroissante, puis identifiant ; pagination par curseur. Statut calculé \
          (draft, due, partial, paid, void ; issued pour un avoir) et retard au jour de \
          Europe/Paris. q cherche, sans tenir compte de la casse ni des accents, dans le \
          numéro, le nom du destinataire et le nom affiché du patient.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Une page de factures."),
    @ApiResponse(
        responseCode = "404",
        description = "PATIENT_NOT_FOUND : patientId inexistant ou d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : status ou kind INVALID_VALUE ; q OUT_OF_RANGE"
                + " (moins de 2 ou plus de 100 caractères) ; limit OUT_OF_RANGE (1 à 100) ou"
                + " INVALID_FORMAT ; cursor INVALID_FORMAT ; overdue, from, patientId"
                + " INVALID_FORMAT.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  InvoicePageDto listInvoices(
      @Parameter(
              description =
                  "Statut calculé : draft, due, partial, paid ou void. Absent : tous (avoirs"
                      + " compris).",
              example = "due")
          @RequestParam(required = false)
          String status,
      @Parameter(description = "true : en retard seulement ; false : pas en retard.")
          @RequestParam(required = false)
          Boolean overdue,
      @Parameter(description = "Nature : invoice ou credit_note. Absent : les deux.")
          @RequestParam(required = false)
          String kind,
      @Parameter(description = "Un seul patient.", example = ID_EXAMPLE)
          @RequestParam(required = false)
          UUID patientId,
      @Parameter(
              description =
                  "Recherche, 2 à 100 caractères : numéro, nom du destinataire ou nom affiché"
                      + " du patient.",
              example = "roux")
          @RequestParam(required = false)
          String q,
      @Parameter(
              description = "Date d'émission minimale incluse (AAAA-MM-JJ).",
              example = "2026-01-01")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "Curseur opaque (nextCursor de la page précédente).")
          @RequestParam(required = false)
          String cursor,
      @Parameter(
              description = "Taille de la page, de 1 à 100 ; 50 par défaut.",
              schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100"))
          @RequestParam(required = false)
          Integer limit) {
    return list.list(status, overdue, kind, patientId, q, from, cursor, limit);
  }

  @GetMapping("/api/invoices/{id}")
  @Operation(
      summary = "Lit une facture",
      description =
          """
          Session praticien. Facture complète : lignes, émetteur, destinataire, montants, \
          historique. Pour un brouillon, l'émetteur reflète les réglages du moment, la TVA est \
          calculée avec eux et nextNumberPreview donne le numéro qu'il recevrait s'il était \
          émis maintenant ; pour un document émis, l'instantané de l'émission.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La facture."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  InvoiceDto getInvoice(
      @Parameter(description = "Identifiant de la facture.", example = ID_EXAMPLE) @PathVariable
          UUID id) {
    return invoices.get(id);
  }

  @PatchMapping("/api/invoices/{id}")
  @Operation(
      summary = "Modifie un brouillon",
      description =
          """
          Session praticien. Brouillon seulement. Fusion partielle : un champ absent reste \
          inchangé. patientId : tant que le brouillon n'a aucune ligne de séance (le \
          destinataire repasse à celui du nouveau patient, sauf si recipient est donné) ; \
          recipient : null revient au destinataire par défaut ; dueTerm date exige dueOn ; \
          footerNote null l'efface ; lines remplace toutes les lignes (100 lignes libres et 200 \
          lignes de séance au plus) : une ligne de séance retirée rend la séance à facturer \
          (pending), une séance ajoutée passe à invoiced. Montants recalculés. Répond la \
          facture relue.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Brouillon modifié."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            NOT_FOUND
                + " PATIENT_NOT_FOUND : nouveau patient inexistant ou d'un autre praticien."
                + " SESSION_NOT_BILLABLE : voir 409.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = ISSUED + " PATIENT_ARCHIVED : nouveau patient archivé. " + SESSION_CODES,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : patientId REQUIRED, INVALID_FORMAT ou"
                + " INVALID_VALUE (le brouillon garde des lignes de séance) ; recipient.name"
                + " REQUIRED ou OUT_OF_RANGE ; recipient.addressLines OUT_OF_RANGE ; issueDate"
                + " REQUIRED ou INVALID_FORMAT ; dueTerm REQUIRED ou INVALID_VALUE ; dueOn"
                + " REQUIRED (échéance date), OUT_OF_RANGE (avant la date d'émission),"
                + " INVALID_VALUE (hors échéance date) ou INVALID_FORMAT ; footerNote"
                + " OUT_OF_RANGE (500 caractères) ; lines REQUIRED (null) ;"
                + LINE_FIELDS,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  InvoiceDto updateInvoice(
      @Parameter(description = "Identifiant de la facture.", example = ID_EXAMPLE) @PathVariable
          UUID id,
      @Valid @RequestBody UpdateInvoiceRequest request) {
    return invoices.update(id, request);
  }

  @DeleteMapping("/api/invoices/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Supprime un brouillon",
      description =
          "Session praticien. Brouillon seulement : suppression physique (E9), avec ses lignes et"
              + " son historique ; ses séances reviennent à facturer (pending).")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Brouillon supprimé."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = ISSUED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  void deleteInvoice(
      @Parameter(description = "Identifiant de la facture.", example = ID_EXAMPLE) @PathVariable
          UUID id) {
    invoices.delete(id);
  }
}
