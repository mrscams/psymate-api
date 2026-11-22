package com.psymate.agenda.controller;

import com.psymate.agenda.dto.BookingTokenRequest;
import com.psymate.agenda.dto.CreateBookingRequest;
import com.psymate.agenda.dto.PublicAppointmentDto;
import com.psymate.agenda.dto.PublicBookingDto;
import com.psymate.agenda.dto.PublicSlotsDto;
import com.psymate.agenda.service.BookingRequestService;
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
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prise de rendez-vous en ligne, routes publiques (G Q71, K5.1) : réglages et créneaux en lecture
 * (M14-05) ; demande, vérification de l'e-mail, lien de gestion et annulation (M14-06). Sans
 * session ni cookie, dans la chaîne publique de {@code directory.config.PublicSecurityConfig} (60
 * requêtes par minute et par IP, 5 demandes par heure et par IP), {@code Cache-Control: no-store}
 * sur toute réponse (posé aussi sur les erreurs par {@code agenda.config.PublicBookingWebConfig}).
 * Un seul 404 pour toutes les causes de « non offerte », et aucune réponse avant la vérification ne
 * dépend de l'identité saisie (G Q78). Les jetons voyagent dans le corps, jamais dans l'URL (G
 * Q80).
 */
@RestController
@RequestMapping("/api/public/booking")
@Tag(
    name = "Prise de rendez-vous en ligne",
    description =
        "Pages publiques de prise de rendez-vous d'un praticien, sans compte ni session : réglages"
            + " publics, créneaux offerts, demande de rendez-vous, vérification de l'e-mail par un"
            + " lien à usage unique, puis suivi et annulation par un lien de gestion personnel."
            + " Aucune réponse ne révèle un rendez-vous, un nom ou un créneau pris ; « non"
            + " offerte » a une seule réponse, quelle qu'en soit la cause, et la réponse à une"
            + " demande ne dépend jamais de l'adresse saisie.")
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

  private static final String TOKEN_INVALID =
      "BOOKING_TOKEN_INVALID : jeton inconnu (jamais émis, remplacé, ou demande purgée).";

  private static final String TOKEN_INVALID_EXAMPLE =
      """
      {"type":"about:blank","title":"Not Found","status":404,\
      "detail":"Ce lien n'est pas valable.",\
      "instance":"/api/public/booking","code":"BOOKING_TOKEN_INVALID"}""";

  private static final String TOKEN_VALIDATION =
      "VALIDATION_FAILED avec errors[] {field, code, message} : token REQUIRED (absent ou vide) ou"
          + " OUT_OF_RANGE (plus de 200 caractères).";

  private final PublicBookingService service;
  private final BookingRequestService requests;

  PublicBookingController(PublicBookingService service, BookingRequestService requests) {
    this.service = service;
    this.requests = requests;
  }

  /**
   * 200 ; Cache-Control: no-store est posé une seule fois, pour toutes les réponses de ces routes
   * (erreurs comprises), par {@code agenda.config.PublicBookingWebConfig}.
   */
  private static <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok(body);
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

  // ---- M14-06 : demande, vérification, gestion

  @PostMapping("/practitioners/{slug}/requests")
  @Operation(
      operationId = "createBookingRequest",
      summary = "Demande un rendez-vous en ligne ; un lien de vérification part par e-mail",
      description =
          """
          Public, sans session ni cookie ; 5 demandes par heure et par IP (429 RATE_LIMITED avec \
          Retry-After), en plus de 60 requêtes par minute et par IP ; Cache-Control: no-store. \
          Vérifications dans cet ordre : forme du corps (422 par champ) ; 404 \
          BOOKING_NOT_AVAILABLE si la prise de rendez-vous n'est pas offerte ; 409 \
          SLOT_NOT_AVAILABLE si startsAt n'est pas exactement le début d'un créneau offert (même \
          calcul que GET …/slots) ou si la modalité n'est pas offerte. Sinon 202 sans corps, \
          quelle que soit l'adresse saisie (G Q78) : la demande est enregistrée et un lien de \
          vérification, valable 30 minutes, part par e-mail après la réponse ; ou bien elle est \
          ignorée en silence, sans trace ni e-mail (pot de miel rempli, formulaire envoyé moins de \
          3 secondes après son affichage ou dans le futur, déjà 3 demandes dans l'heure pour \
          cette adresse). Rien n'est réservé et le praticien n'est pas prévenu avant la \
          vérification ; une demande jamais vérifiée est effacée après 24 heures. Un échec \
          d'envoi de l'e-mail ne change pas la réponse.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Demande acceptée, ou ignorée en silence ; aucun corps.",
        headers =
            @Header(
                name = "Cache-Control",
                description = "no-store.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : corps JSON illisible sans champ identifiable.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
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
        responseCode = "409",
        description =
            "SLOT_NOT_AVAILABLE : startsAt n'est pas le début d'un créneau offert (pris, hors"
                + " plage, trop proche, au-delà de l'horizon), ou la modalité n'est pas offerte."
                + " Relire les créneaux.",
        content =
            @Content(
                mediaType = PROBLEM,
                schema = @Schema(implementation = ProblemDetail.class),
                examples =
                    @ExampleObject(
                        name = "SLOT_NOT_AVAILABLE",
                        value =
                            """
                            {"type":"about:blank","title":"Conflict","status":409,\
                            "detail":"Ce créneau n'est pas proposé à la réservation.",\
                            "instance":"/api/public/booking","code":"SLOT_NOT_AVAILABLE"}"""))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} : startsAt, modality,"
                + " firstName, lastName, email, consent, formShownAt REQUIRED (absents ou vides) ;"
                + " firstName, lastName (100), email (320), phone (30) OUT_OF_RANGE ; email,"
                + " phone, startsAt, formShownAt INVALID_FORMAT ; modality, locale INVALID_VALUE"
                + " (valeur non admise) ; consent INVALID_VALUE (différent de true).",
        content =
            @Content(
                mediaType = PROBLEM,
                schema = @Schema(implementation = ProblemDetail.class),
                examples =
                    @ExampleObject(
                        name = "INVALID_FORMAT",
                        value =
                            """
                            {"type":"about:blank","title":"Requête invalide","status":422,\
                            "detail":"Certains champs sont invalides.",\
                            "instance":"/api/public/booking","code":"VALIDATION_FAILED",\
                            "errors":[{"field":"email","code":"INVALID_FORMAT",\
                            "message":"E-mail invalide : une adresse de la forme nom@domaine.fr\
                             est attendue."}]}"""))),
    @ApiResponse(
        responseCode = "429",
        description =
            "RATE_LIMITED : plus de 5 demandes par heure, ou plus de 60 requêtes par minute,"
                + " depuis cette adresse IP ; en-tête Retry-After et champ retryAfterSeconds en"
                + " secondes.",
        headers =
            @Header(
                name = "Retry-After",
                description = "Secondes à attendre avant une nouvelle requête.",
                schema = @Schema(type = "integer")),
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> createBookingRequest(
      @Parameter(description = "Adresse publique (slug) du praticien.", example = "claire-lambert")
          @PathVariable
          String slug,
      @Valid @RequestBody CreateBookingRequest request) {
    requests.create(slug, request);
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/requests/verify")
  @Operation(
      operationId = "verifyBookingRequest",
      summary = "Vérifie l'e-mail d'une demande et crée le rendez-vous",
      description =
          """
          Public, sans session ni cookie ; 60 requêtes par minute et par IP ; Cache-Control: \
          no-store. Le jeton du lien de vérification (/rendez-vous/confirmer/{jeton}) voyage dans \
          le corps. À la première vérification réussie, le rendez-vous est créé : en attente de \
          la réponse du praticien (status pending) si le praticien valide chaque demande, \
          confirmé (status confirmed) en acceptation automatique ; le mode appliqué est celui du \
          moment de la vérification. La réponse porte alors manageToken, le jeton du lien de \
          gestion, renvoyé cette seule fois ; un e-mail le rappelle au demandeur et le praticien \
          est prévenu. Un second appel avec le même jeton rend l'état courant du même rendez-vous, \
          sans manageToken et sans nouvel e-mail. Le premier e-mail vérifié obtient le créneau : \
          409 SLOT_TAKEN s'il n'est plus offert, 409 BOOKING_ALREADY_ACTIVE si cette adresse a \
          déjà un rendez-vous en ligne actif et à venir avec ce praticien ; dans ces deux cas \
          rien n'est créé. Vérifications dans cet ordre : jeton inconnu (404), déjà vérifié \
          (200), expiré (410), prise de rendez-vous plus offerte (404), rendez-vous déjà actif \
          (409), créneau pris (409).""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "Rendez-vous créé (avec manageToken), ou déjà créé par ce jeton (sans manageToken).",
        headers =
            @Header(
                name = "Cache-Control",
                description = "no-store.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "404",
        description =
            TOKEN_INVALID
                + " Ou BOOKING_NOT_AVAILABLE : la prise de rendez-vous n'est plus offerte par ce"
                + " praticien (fonction désactivée, fiche dépubliée, praticien non approuvé).",
        content =
            @Content(
                mediaType = PROBLEM,
                schema = @Schema(implementation = ProblemDetail.class),
                examples = {
                  @ExampleObject(name = "BOOKING_TOKEN_INVALID", value = TOKEN_INVALID_EXAMPLE),
                  @ExampleObject(name = "BOOKING_NOT_AVAILABLE", value = NOT_AVAILABLE_EXAMPLE)
                })),
    @ApiResponse(
        responseCode = "409",
        description =
            "SLOT_TAKEN : le créneau n'est plus offert (pris entre-temps, ou plus proposé)."
                + " BOOKING_ALREADY_ACTIVE : cette adresse a déjà un rendez-vous en ligne actif et"
                + " à venir avec ce praticien. Rien n'est créé.",
        content =
            @Content(
                mediaType = PROBLEM,
                schema = @Schema(implementation = ProblemDetail.class),
                examples = {
                  @ExampleObject(
                      name = "SLOT_TAKEN",
                      value =
                          """
                          {"type":"about:blank","title":"Conflict","status":409,\
                          "detail":"Ce créneau vient d'être pris.",\
                          "instance":"/api/public/booking","code":"SLOT_TAKEN"}"""),
                  @ExampleObject(
                      name = "BOOKING_ALREADY_ACTIVE",
                      value =
                          """
                          {"type":"about:blank","title":"Conflict","status":409,\
                          "detail":"Vous avez déjà un rendez-vous à venir avec ce praticien.",\
                          "instance":"/api/public/booking","code":"BOOKING_ALREADY_ACTIVE"}""")
                })),
    @ApiResponse(
        responseCode = "410",
        description =
            "BOOKING_TOKEN_EXPIRED : lien de vérification expiré (30 minutes) sans avoir été"
                + " suivi ; il faut refaire la demande.",
        content =
            @Content(
                mediaType = PROBLEM,
                schema = @Schema(implementation = ProblemDetail.class),
                examples =
                    @ExampleObject(
                        name = "BOOKING_TOKEN_EXPIRED",
                        value =
                            """
                            {"type":"about:blank","title":"Gone","status":410,\
                            "detail":"Ce lien a expiré.",\
                            "instance":"/api/public/booking","code":"BOOKING_TOKEN_EXPIRED"}"""))),
    @ApiResponse(
        responseCode = "422",
        description = TOKEN_VALIDATION,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
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
  ResponseEntity<PublicAppointmentDto> verifyBookingRequest(
      @Valid @RequestBody BookingTokenRequest request) {
    return noStore(requests.verify(request.token()));
  }

  @PostMapping("/manage")
  @Operation(
      operationId = "getManagedBooking",
      summary = "Lit l'état d'un rendez-vous pris en ligne, par son lien de gestion",
      description =
          """
          Public, sans session ni cookie ; 60 requêtes par minute et par IP ; Cache-Control: \
          no-store. Le jeton du lien de gestion (/rendez-vous/gerer/{jeton}) voyage dans le \
          corps. Rend l'état du rendez-vous (pending, confirmed, cancelled avec cancelledBy, \
          past), ses horaires, sa modalité et le praticien ; jamais le nom, l'e-mail ni le \
          téléphone du demandeur. Le lien reste valable même si le praticien a désactivé la \
          prise de rendez-vous ou dépublié sa fiche (slug et displayName valent alors null).""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "État du rendez-vous (manageToken toujours null).",
        headers =
            @Header(
                name = "Cache-Control",
                description = "no-store.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "404",
        description = TOKEN_INVALID,
        content =
            @Content(
                mediaType = PROBLEM,
                schema = @Schema(implementation = ProblemDetail.class),
                examples =
                    @ExampleObject(name = "BOOKING_TOKEN_INVALID", value = TOKEN_INVALID_EXAMPLE))),
    @ApiResponse(
        responseCode = "422",
        description = TOKEN_VALIDATION,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
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
  ResponseEntity<PublicAppointmentDto> getManagedBooking(
      @Valid @RequestBody BookingTokenRequest request) {
    return noStore(requests.manage(request.token()));
  }

  @PostMapping("/manage/cancel")
  @Operation(
      operationId = "cancelManagedBooking",
      summary = "Annule un rendez-vous pris en ligne, par son lien de gestion",
      description =
          """
          Public, sans session ni cookie ; 60 requêtes par minute et par IP ; Cache-Control: \
          no-store. Le jeton du lien de gestion voyage dans le corps. Annulation ordinaire : le \
          rendez-vous passe à cancelled (cancelledBy = visitor) et le praticien est prévenu par \
          e-mail. Idempotente : sur un rendez-vous déjà annulé, quelle que soit la cause, rend \
          son état sans rien changer ni envoyer. 409 APPOINTMENT_NOT_ACTIVE si le rendez-vous a \
          commencé ou est terminé.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Rendez-vous annulé (ou déjà annulé) ; manageToken toujours null.",
        headers =
            @Header(
                name = "Cache-Control",
                description = "no-store.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "404",
        description = TOKEN_INVALID,
        content =
            @Content(
                mediaType = PROBLEM,
                schema = @Schema(implementation = ProblemDetail.class),
                examples =
                    @ExampleObject(name = "BOOKING_TOKEN_INVALID", value = TOKEN_INVALID_EXAMPLE))),
    @ApiResponse(
        responseCode = "409",
        description =
            "APPOINTMENT_NOT_ACTIVE : le rendez-vous a commencé ou est terminé ; il ne peut plus"
                + " être annulé.",
        content =
            @Content(
                mediaType = PROBLEM,
                schema = @Schema(implementation = ProblemDetail.class),
                examples =
                    @ExampleObject(
                        name = "APPOINTMENT_NOT_ACTIVE",
                        value =
                            """
                            {"type":"about:blank","title":"Conflict","status":409,\
                            "detail":"Ce rendez-vous a commencé ou est terminé : il ne peut plus\
                             être annulé.",\
                            "instance":"/api/public/booking","code":"APPOINTMENT_NOT_ACTIVE"}"""))),
    @ApiResponse(
        responseCode = "422",
        description = TOKEN_VALIDATION,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
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
  ResponseEntity<PublicAppointmentDto> cancelManagedBooking(
      @Valid @RequestBody BookingTokenRequest request) {
    return noStore(requests.cancel(request.token()));
  }
}
