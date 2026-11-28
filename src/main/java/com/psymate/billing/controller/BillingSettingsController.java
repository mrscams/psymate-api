package com.psymate.billing.controller;

import com.psymate.billing.dto.BillingIbanDeleteRequest;
import com.psymate.billing.dto.BillingIbanRequest;
import com.psymate.billing.dto.BillingSettingsDto;
import com.psymate.billing.dto.BillingSettingsRequest;
import com.psymate.billing.service.BillingIbanService;
import com.psymate.billing.service.BillingSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Réglages de facturation du praticien et IBAN (M15-06, L5.1, fiche 39 du web). L'IBAN n'est jamais
 * renvoyé ni journalisé (G Q95).
 */
@Tag(
    name = "Facturation : réglages",
    description =
        "Réglages de facturation du praticien : identité sur les factures (lecture seule), SIRET,"
            + " coordonnées, régime de TVA et sa mention, numérotation, modalités de règlement,"
            + " modèles d'e-mail ; IBAN rangé chiffré, jamais renvoyé, protégé par le mot de passe"
            + " du compte.")
@RestController
class BillingSettingsController {

  private static final String PROBLEM = "application/problem+json";
  private static final String MALFORMED = "MALFORMED_REQUEST : corps JSON absent ou illisible.";

  private final BillingSettingsService settings;
  private final BillingIbanService ibans;

  BillingSettingsController(BillingSettingsService settings, BillingIbanService ibans) {
    this.settings = settings;
    this.ibans = ibans;
  }

  @GetMapping("/api/me/billing-settings")
  @Operation(
      summary = "Réglages de facturation",
      description =
          """
          Session praticien. Les réglages existent toujours : sans réglages enregistrés, valeurs \
          par défaut (régime health_exempt, préfixes F et AV, échéance on_receipt, modes \
          transfer, cheque, cash), adresse et e-mail proposés depuis le dossier professionnel \
          tant qu'ils ne sont pas enregistrés. identity est en lecture seule. vat.mention est \
          fournie par l'application, en français quelle que soit la langue servie. \
          numbering.next* sont les prochains numéros de l'année en cours (Europe/Paris) ; \
          numbering.locked est vrai dès qu'un numéro a été attribué. missing liste ce qui \
          empêche d'émettre une facture (siret, addressLines, service). L'IBAN n'est jamais \
          renvoyé : payment.ibanLast4 seulement.\
          """)
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Réglages de facturation.")})
  BillingSettingsDto getBillingSettings() {
    return settings.get();
  }

  @PutMapping("/api/me/billing-settings")
  @Operation(
      summary = "Remplace les réglages de facturation",
      description =
          """
          Session praticien. Remplace tous les champs modifiables : un texte absent, null ou vide \
          est effacé ; un bloc absent ou null (vat, numbering, payment, emailTemplates) prend ses \
          valeurs par défaut. Les règles de champ sont toutes rapportées ensemble (422). Les \
          préfixes ne changent plus dès qu'un numéro a été attribué (409). Les champs en lecture \
          seule de la réponse et tout autre champ sont refusés (UNKNOWN_FIELD). Répond les \
          réglages enregistrés.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Réglages enregistrés."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "NUMBERING_LOCKED : un préfixe change alors qu'un numéro de facture ou d'avoir a déjà"
                + " été attribué.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : siret SIRET_INVALID (pas 14 chiffres, ou clé de"
                + " Luhn fausse) ; vat.rateBp VAT_RATE_REQUIRED (régime liable sans taux) ou"
                + " OUT_OF_RANGE (hors 0 à 10000) ; vat.vatNumber VAT_NUMBER_REQUIRED (régime"
                + " liable sans numéro), OUT_OF_RANGE ou INVALID_VALUE ; vat.regime,"
                + " payment.dueTerm, payment.acceptedModes (vide ou mode inconnu),"
                + " numbering.invoicePrefix, numbering.creditNotePrefix (caractère refusé, ou égal"
                + " au préfixe des factures), displayEmail, displayPhone,"
                + " emailTemplates.*.subject (saut de ligne) INVALID_VALUE ; OUT_OF_RANGE (texte"
                + " trop long) ; INVALID_FORMAT (type JSON) ; UNKNOWN_FIELD (champ refusé).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  BillingSettingsDto putBillingSettings(@Valid @RequestBody BillingSettingsRequest request) {
    return settings.replace(request);
  }

  @PutMapping("/api/me/billing-settings/iban")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Enregistre l'IBAN",
      description =
          """
          Session praticien. Exige le mot de passe actuel du compte. L'IBAN est normalisé \
          (espaces retirés, majuscules), contrôlé (format et clé mod 97), puis rangé chiffré hors \
          de la base ; seuls ses quatre derniers caractères sont gardés (payment.ibanLast4 des \
          réglages). Il n'est jamais renvoyé. Remplace l'IBAN précédent. Tout champ autre que \
          iban et currentPassword est refusé (UNKNOWN_FIELD).\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "IBAN enregistré."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PASSWORD_INVALID : le mot de passe ne correspond pas au compte.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : iban IBAN_INVALID (format ou clé mod 97) ou"
                + " REQUIRED ; currentPassword REQUIRED ou OUT_OF_RANGE ; INVALID_FORMAT ;"
                + " UNKNOWN_FIELD.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  void putIban(@Valid @RequestBody BillingIbanRequest request) {
    ibans.replace(request);
  }

  @DeleteMapping("/api/me/billing-settings/iban")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Efface l'IBAN",
      description =
          """
          Session praticien. Exige le mot de passe actuel du compte, dans le corps. L'IBAN rangé \
          est effacé et payment.ibanLast4 passe à null. Idempotent : sans IBAN, 204. Tout champ \
          autre que currentPassword est refusé (UNKNOWN_FIELD).\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "IBAN effacé."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PASSWORD_INVALID : le mot de passe ne correspond pas au compte.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : currentPassword REQUIRED ou OUT_OF_RANGE ;"
                + " INVALID_FORMAT ; UNKNOWN_FIELD.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  void deleteIban(@Valid @RequestBody BillingIbanDeleteRequest request) {
    ibans.delete(request);
  }
}
