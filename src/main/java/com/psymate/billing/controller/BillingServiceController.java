package com.psymate.billing.controller;

import com.psymate.billing.dto.BillingServiceDto;
import com.psymate.billing.dto.BillingServicesDto;
import com.psymate.billing.dto.CreateBillingServiceRequest;
import com.psymate.billing.dto.UpdateBillingServiceRequest;
import com.psymate.billing.model.BillingException;
import com.psymate.billing.service.BillingCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prestations du praticien (M15-05, L5.1, fiche 39) : liste, création, modification, archivage.
 * Montants en centimes d'euro entiers (G Q84).
 */
@Tag(
    name = "Facturation : prestations",
    description =
        "Prestations du praticien et leurs prix, en centimes d'euro entiers (6000 = 60,00 €)."
            + " Une prestation par défaut au plus, 50 prestations actives au plus. Une prestation"
            + " ne se supprime jamais : elle s'archive.")
@RestController
class BillingServiceController {

  private static final String PROBLEM = "application/problem+json";
  private static final String ID_EXAMPLE = "5d2f6a1e-8b3c-4d7e-9f10-2a3b4c5d6e7f";
  private static final String NOT_FOUND =
      "SERVICE_NOT_FOUND : prestation inexistante, archivée ou d'un autre praticien.";
  private static final String MALFORMED =
      "MALFORMED_REQUEST : identifiant qui n'est pas un UUID, ou corps JSON illisible.";

  private final BillingCatalogService catalog;

  BillingServiceController(BillingCatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/api/me/billing-services")
  @Operation(
      summary = "Prestations du praticien",
      description =
          """
          Session praticien. Prestations non archivées du praticien courant : la prestation par \
          défaut d'abord, puis par libellé. Prix en centimes d'euro entiers.\
          """)
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Prestations.")})
  BillingServicesDto listServices() {
    return catalog.list();
  }

  @PostMapping("/api/me/billing-services")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Crée une prestation",
      description =
          """
          Session praticien. Le praticien et le cabinet viennent de la session. La première \
          prestation créée devient la prestation par défaut, quel que soit isDefault ; avec \
          isDefault = true, la nouvelle prestation devient la prestation par défaut et retire le \
          défaut de l'autre. 50 prestations non archivées au plus. price est un entier en \
          centimes d'euro (6000 = 60,00 €) : un nombre décimal ou une chaîne est refusé \
          (INVALID_FORMAT). Tout champ autre que label, durationMin, price et isDefault est \
          refusé (UNKNOWN_FIELD).\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Prestation créée."),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : corps JSON absent ou illisible.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "SERVICE_LIMIT_REACHED : le praticien a déjà 50 prestations non archivées ;"
                + " VALIDATION_FAILED avec errors[] : label REQUIRED ou OUT_OF_RANGE (plus de 120"
                + " caractères) ; price REQUIRED, OUT_OF_RANGE (zéro, négatif ou au-delà d'un"
                + " entier 32 bits) ou INVALID_FORMAT (nombre décimal, chaîne) ; durationMin"
                + " OUT_OF_RANGE (hors 1 à 600) ou INVALID_FORMAT ; isDefault INVALID_FORMAT (pas"
                + " un booléen) ; UNKNOWN_FIELD (champ refusé).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  BillingServiceDto createService(@Valid @RequestBody CreateBillingServiceRequest request) {
    return catalog.create(request);
  }

  @PatchMapping("/api/me/billing-services/{id}")
  @Operation(
      summary = "Modifie une prestation",
      description =
          """
          Session praticien. Fusion partielle : label, price et isDefault absents ou null restent \
          inchangés ; durationMin à null efface la durée. isDefault = true fait de cette \
          prestation la prestation par défaut et retire le défaut de l'autre ; isDefault = false \
          sur la prestation par défaut est refusé (il faut en désigner une autre). La prestation \
          d'un autre praticien, ou archivée, répond 404. Tout champ autre que label, durationMin, \
          price et isDefault est refusé (UNKNOWN_FIELD).\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Prestation modifiée."),
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
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : label REQUIRED (vide) ou OUT_OF_RANGE (plus de 120"
                + " caractères) ; price OUT_OF_RANGE (zéro, négatif ou au-delà d'un entier 32"
                + " bits) ou INVALID_FORMAT (nombre décimal, chaîne) ; durationMin OUT_OF_RANGE"
                + " (hors 1 à 600) ou INVALID_FORMAT ; isDefault INVALID_VALUE (false sur la"
                + " prestation par défaut) ou INVALID_FORMAT ; UNKNOWN_FIELD (champ refusé).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  BillingServiceDto updateService(
      @Parameter(description = "Identifiant de la prestation.", example = ID_EXAMPLE) @PathVariable
          UUID id,
      @Valid @RequestBody UpdateBillingServiceRequest request) {
    if (request.getLabel() != null && request.getLabel().isBlank()) {
      throw BillingException.invalidField("label", "REQUIRED");
    }
    return catalog.update(id, request);
  }

  @DeleteMapping("/api/me/billing-services/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Archive une prestation",
      description =
          """
          Session praticien. La prestation est archivée, jamais supprimée : elle disparaît de la \
          liste. Idempotent : une prestation déjà archivée répond 204. La prestation par défaut \
          ne s'archive pas tant qu'il reste d'autres prestations (409) ; seule, elle s'archive. \
          La prestation d'un autre praticien répond 404.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Prestation archivée."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "SERVICE_NOT_FOUND : prestation inexistante ou d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "SERVICE_IS_DEFAULT : prestation par défaut alors que d'autres prestations restent ;"
                + " en désigner une autre par défaut d'abord.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  void archiveService(
      @Parameter(description = "Identifiant de la prestation.", example = ID_EXAMPLE) @PathVariable
          UUID id) {
    catalog.archive(id);
  }
}
