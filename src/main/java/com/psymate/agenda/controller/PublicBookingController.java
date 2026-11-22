package com.psymate.agenda.controller;

import com.psymate.agenda.dto.PublicBookingDto;
import com.psymate.agenda.dto.PublicSlotsDto;
import com.psymate.agenda.service.PublicBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prise de rendez-vous en ligne, routes publiques en lecture (M14-05, G Q71, K5.1) : sans session
 * ni cookie, dans la chaîne publique de {@code directory.config.PublicSecurityConfig} (60 requêtes
 * par minute et par IP), {@code Cache-Control: no-store} sur toute réponse (posé aussi sur les
 * erreurs par {@code agenda.config.PublicBookingWebConfig}). Un seul 404 pour toutes les causes (G
 * Q78).
 */
@RestController
@RequestMapping("/api/public/booking")
@Tag(
    name = "Prise de rendez-vous en ligne",
    description =
        "Pages publiques de prise de rendez-vous d'un praticien, sans compte ni session : réglages"
            + " publics et créneaux offerts. Aucune réponse ne révèle un rendez-vous, un nom ou un"
            + " créneau pris ; « non offerte » a une seule réponse, quelle qu'en soit la cause.")
class PublicBookingController {

  private static final String PROBLEM = "application/problem+json";

  private static final String NOT_AVAILABLE =
      "BOOKING_NOT_AVAILABLE : prise de rendez-vous non offerte à cette adresse. Même corps pour"
          + " toutes les causes, sans les distinguer (G Q78) : slug inconnu, fiche non publiée,"
          + " praticien non approuvé (suspendu, rejeté, clos), fonction désactivée, aucune plage"
          + " ouverte.";

  private static final String NOT_AVAILABLE_EXAMPLE =
      """
      {"type":"about:blank","title":"Not Found","status":404,\
      "detail":"Prise de rendez-vous en ligne indisponible à cette adresse.",\
      "instance":"/api/public/booking","code":"BOOKING_NOT_AVAILABLE"}""";

  private static final String RATE_LIMITED =
      "RATE_LIMITED : plus de 60 requêtes par minute depuis cette adresse IP sur les routes"
          + " publiques ; en-tête Retry-After et champ retryAfterSeconds en secondes.";

  private final PublicBookingService service;

  PublicBookingController(PublicBookingService service) {
    this.service = service;
  }

  private static <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }

  @GetMapping("/practitioners/{slug}")
  @Operation(
      operationId = "getPublicBooking",
      summary = "Dit si la prise de rendez-vous en ligne est offerte, et avec quels réglages",
      description =
          """
          Public, sans session ni cookie ; 60 requêtes par minute et par IP ; Cache-Control: \
          no-store. La prise de rendez-vous est offerte si la fiche du praticien est publiée, le \
          praticien approuvé, la fonction activée (booking.enabled) et au moins une plage \
          ouverte. La réponse donne le mode de confirmation, la durée d'un créneau, les \
          modalités, le fuseau, le délai minimal et l'horizon ; jamais les plages ouvertes ni les \
          rendez-vous. Sinon 404 BOOKING_NOT_AVAILABLE, même corps quelle que soit la cause.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Prise de rendez-vous offerte ; réglages publics.",
        headers =
            @Header(
                name = "Cache-Control",
                description = "no-store.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_AVAILABLE,
        content =
            @Content(
                mediaType = PROBLEM,
                schema = @Schema(implementation = ProblemDetail.class),
                examples =
                    @ExampleObject(name = "BOOKING_NOT_AVAILABLE", value = NOT_AVAILABLE_EXAMPLE))),
    @ApiResponse(
        responseCode = "429",
        description = RATE_LIMITED,
        headers =
            @Header(
                name = "Retry-After",
                description = "Secondes à attendre avant une nouvelle requête.",
                schema = @Schema(type = "integer")),
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<PublicBookingDto> settings(
      @Parameter(description = "Adresse publique (slug) du praticien.", example = "claire-lambert")
          @PathVariable
          String slug) {
    return noStore(service.settings(slug));
  }

  @GetMapping("/practitioners/{slug}/slots")
  @Operation(
      operationId = "listPublicBookingSlots",
      summary = "Liste les créneaux offerts d'un praticien sur une fenêtre de dates",
      description =
          """
          Public, sans session ni cookie ; 60 requêtes par minute et par IP ; Cache-Control: \
          no-store. from et to sont des dates AAAA-MM-JJ dans le fuseau du praticien (timezone \
          de la réponse), to inclus ; from absent = aujourd'hui dans ce fuseau, to absent = from \
          + 13 jours ; 31 jours au plus. Chaque plage ouverte est découpée en créneaux de \
          slotDurationMin minutes à partir de son début (le reste en fin de plage est ignoré). \
          Un créneau est offert s'il commence au plus tôt maintenant + minNoticeHours, au plus \
          tard le jour aujourd'hui + horizonDays, s'il ne chevauche aucun rendez-vous actif du \
          praticien, et si aucun changement d'heure ne le traverse (une heure locale qui n'existe \
          pas n'est jamais offerte, une heure répétée l'est une fois). Seuls les créneaux offerts \
          sont publiés, en instants UTC triés par début : jamais un créneau pris, ni sa raison, \
          ni un identifiant. Les vérifications se font dans cet ordre : 404, to avant from, \
          fenêtre trop large.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Créneaux offerts de la fenêtre (liste vide si aucun n'est libre).",
        headers =
            @Header(
                name = "Cache-Control",
                description = "no-store.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_AVAILABLE,
        content =
            @Content(
                mediaType = PROBLEM,
                schema = @Schema(implementation = ProblemDetail.class),
                examples =
                    @ExampleObject(name = "BOOKING_NOT_AVAILABLE", value = NOT_AVAILABLE_EXAMPLE))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} : to INVALID_VALUE (to"
                + " précède from) ; from ou to INVALID_FORMAT (date illisible, AAAA-MM-JJ"
                + " attendu). WINDOW_TOO_LARGE avec errors[0].field = to : plus de 31 jours, to"
                + " inclus.",
        content =
            @Content(
                mediaType = PROBLEM,
                schema = @Schema(implementation = ProblemDetail.class),
                examples =
                    @ExampleObject(
                        name = "WINDOW_TOO_LARGE",
                        value =
                            """
                            {"type":"about:blank","title":"Unprocessable Content","status":422,\
                            "detail":"La fenêtre demandée dépasse 31 jours.",\
                            "instance":"/api/public/booking","code":"WINDOW_TOO_LARGE",\
                            "errors":[{"field":"to","code":"WINDOW_TOO_LARGE",\
                            "message":"La fenêtre demandée dépasse 31 jours."}]}"""))),
    @ApiResponse(
        responseCode = "429",
        description = RATE_LIMITED,
        headers =
            @Header(
                name = "Retry-After",
                description = "Secondes à attendre avant une nouvelle requête.",
                schema = @Schema(type = "integer")),
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<PublicSlotsDto> slots(
      @Parameter(description = "Adresse publique (slug) du praticien.", example = "claire-lambert")
          @PathVariable
          String slug,
      @Parameter(
              description =
                  "Premier jour, inclus, AAAA-MM-JJ dans le fuseau du praticien. Défaut :"
                      + " aujourd'hui dans ce fuseau.",
              example = "2026-12-07")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(
              description =
                  "Dernier jour, inclus, AAAA-MM-JJ dans le fuseau du praticien, au plus 30 jours"
                      + " après from. Défaut : from + 13 jours.",
              example = "2026-12-20")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return noStore(service.slots(slug, from, to));
  }
}
