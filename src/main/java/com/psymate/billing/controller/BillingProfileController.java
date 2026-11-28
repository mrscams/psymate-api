package com.psymate.billing.controller;

import com.psymate.billing.dto.BillingProfileDto;
import com.psymate.billing.dto.BillingProfileRequest;
import com.psymate.billing.service.BillingProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profil de facturation d'un patient (M15-05, L5.1, fiche 38) : tarif, adresse de facturation,
 * tiers payeur. Tarif en centimes d'euro entiers (G Q84).
 */
@Tag(
    name = "Facturation : profil du patient",
    description =
        "Profil de facturation d'un patient : tarif en centimes d'euro entiers (5500 = 55,00 €),"
            + " adresse de facturation, tiers payeur. Ce n'est pas une donnée clinique.")
@RestController
class BillingProfileController {

  private static final String PROBLEM = "application/problem+json";
  private static final String ID_EXAMPLE = "5d2f6a1e-8b3c-4d7e-9f10-2a3b4c5d6e7f";
  private static final String NOT_FOUND =
      "PATIENT_NOT_FOUND : patient inexistant ou d'un autre praticien.";
  private static final String MALFORMED =
      "MALFORMED_REQUEST : identifiant qui n'est pas un UUID, ou corps JSON illisible.";

  private final BillingProfileService profiles;

  BillingProfileController(BillingProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping("/api/patients/{id}/billing-profile")
  @Operation(
      summary = "Profil de facturation d'un patient",
      description =
          """
          Session praticien, propriétaire du patient. Tous les champs sont null tant que rien \
          n'est enregistré. Lisible aussi pour un patient archivé. Le patient d'un autre \
          praticien répond 404.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Profil de facturation."),
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
  BillingProfileDto getBillingProfile(
      @Parameter(description = "Identifiant du patient.", example = ID_EXAMPLE) @PathVariable
          UUID id) {
    return profiles.get(id);
  }

  @PutMapping("/api/patients/{id}/billing-profile")
  @Operation(
      summary = "Remplace le profil de facturation d'un patient",
      description =
          """
          Session praticien, propriétaire du patient. Remplace tout le profil : un champ absent, \
          null ou vide est effacé. rate est un entier en centimes d'euro (5500 = 55,00 €) : un \
          nombre décimal ou une chaîne est refusé (INVALID_FORMAT). Refusé pour un patient \
          archivé (409). Tout champ autre que rate, addressLines, thirdPartyName et \
          thirdPartyAddress est refusé (UNKNOWN_FIELD). Répond le profil enregistré.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Profil enregistré."),
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
        description = "PATIENT_ARCHIVED : le patient est archivé.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : rate OUT_OF_RANGE (zéro, négatif ou au-delà d'un"
                + " entier 32 bits) ou INVALID_FORMAT (nombre décimal, chaîne) ; addressLines,"
                + " thirdPartyAddress OUT_OF_RANGE (plus de 300 caractères) ; thirdPartyName"
                + " OUT_OF_RANGE (plus de 200 caractères) ; INVALID_FORMAT (texte attendu) ;"
                + " UNKNOWN_FIELD (champ refusé).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  BillingProfileDto putBillingProfile(
      @Parameter(description = "Identifiant du patient.", example = ID_EXAMPLE) @PathVariable
          UUID id,
      @Valid @RequestBody BillingProfileRequest request) {
    return profiles.replace(id, request);
  }
}
