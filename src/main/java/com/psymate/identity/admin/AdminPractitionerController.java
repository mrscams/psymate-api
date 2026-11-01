package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminAccountDtos.AccountDetailDto;
import com.psymate.identity.admin.AdminAccountDtos.AccountListDto;
import com.psymate.identity.admin.AdminAccountDtos.ReactivateDto;
import com.psymate.identity.admin.AdminAccountDtos.ReasonRequest;
import com.psymate.identity.admin.AdminAccountDtos.SuspendDto;
import com.psymate.identity.api.CurrentPractitioner;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comptes des praticiens pour l'administration (fiche 21, M1-14b). Le rôle admin et la 2FA sont
 * contrôlés en amont par AdminTwoFactorFilter sur tout /api/admin/**.
 */
@Tag(
    name = "Administration : praticiens",
    description =
        "Gestion des comptes par l'administration : liste filtrable avec compteurs par statut,"
            + " détail avec décisions et recours, suspension et réactivation manuelles. Réservé"
            + " aux administrateurs avec double authentification active.")
@RestController
@RequestMapping("/api/admin/practitioners")
class AdminPractitionerController {

  private final AdminPractitionerService service;

  AdminPractitionerController(AdminPractitionerService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listAdminPractitioners",
      summary = "Liste les comptes avec recherche, filtres et compteurs par statut",
      description =
          """
          Administrateur avec 2FA. Liste les comptes (praticiens et administrateurs) du plus \
          récemment créé au plus ancien. Filtres cumulables : q (recherche dans le nom \
          d'affichage et l'e-mail), status, role, profession. counts donne le nombre de comptes \
          par statut (approved, under_review, needs_more, suspended, rejected) pour q, role et \
          profession, sans le filtre status ni la pagination : il sert aux onglets. Pagination \
          par curseur opaque : limit 50 par défaut, bornée à 1..200 sans erreur ; nextCursor \
          est null sur la dernière page.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Page de comptes et compteurs par statut."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ : TOO_LONG"
                + " (q, plus de 100 caractères), INVALID_STATUS (status), INVALID_ROLE (role),"
                + " INVALID_PROFESSION (profession), INVALID_CURSOR (cursor illisible).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  AccountListDto list(
      @Parameter(
              description =
                  "Texte recherché, sans casse, dans le nom d'affichage ou l'e-mail (correspondance"
                      + " sur une partie du texte) ; 100 caractères au plus après suppression des"
                      + " blancs de bord (422 TOO_LONG). Vide ou absent : pas de filtre.",
              example = "martin")
          @RequestParam(required = false)
          String q,
      @Parameter(
              description =
                  "Statut de vérification : email_pending, docs_pending, under_review, approved,"
                      + " needs_more, rejected, suspended ou closed. Absent : tous les statuts."
                      + " Autre valeur : 422 INVALID_STATUS.",
              example = "suspended")
          @RequestParam(required = false)
          String status,
      @Parameter(
              description =
                  "Rôle du compte : practitioner ou admin. Absent : tous les rôles. Autre valeur :"
                      + " 422 INVALID_ROLE.",
              example = "practitioner")
          @RequestParam(required = false)
          String role,
      @Parameter(
              description =
                  "Profession : psychologist, psychiatrist ou coach. Absent : toutes. Autre"
                      + " valeur : 422 INVALID_PROFESSION.",
              example = "psychologist")
          @RequestParam(required = false)
          String profession,
      @Parameter(
              description =
                  "Curseur opaque (nextCursor de la page précédente, à renvoyer tel quel) ;"
                      + " absent pour la première page. Illisible : 422 INVALID_CURSOR.")
          @RequestParam(required = false)
          String cursor,
      @Parameter(
              description =
                  "Nombre maximal de comptes par page : 50 par défaut, ramené dans l'intervalle"
                      + " 1 à 200 sans erreur.",
              example = "50")
          @RequestParam(required = false)
          Integer limit) {
    return service.list(q, status, role, profession, cursor, limit);
  }

  @GetMapping("/{id}")
  @Operation(
      operationId = "getAdminPractitioner",
      summary = "Affiche le compte d'un praticien avec ses décisions et ses recours",
      description =
          """
          Administrateur avec 2FA. Renvoie l'état du compte, l'historique des décisions \
          d'administration (du plus récent au plus ancien) et les recours déposés. Un compte \
          administrateur peut aussi être consulté. Aucune donnée patient.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Détail du compte."),
    @ApiResponse(
        responseCode = "404",
        description = "ADMIN_PRACTITIONER_NOT_FOUND : aucun compte avec cet identifiant.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  AccountDetailDto get(
      @Parameter(
              description = "Identifiant du compte.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/suspend")
  @Operation(
      operationId = "suspendAdminPractitioner",
      summary = "Suspend un compte avec un motif obligatoire",
      description =
          """
          Administrateur avec 2FA. Le motif (20 à 4000 caractères) est \
          contrôlé en premier. Impossible sur un compte déjà suspended, rejected ou closed, sur \
          soi-même, ni sur le dernier administrateur actif. Effets : statut suspended, toutes les \
          sessions révoquées, décision suspended et ligne admin.suspend au journal (le statut \
          précédent y est conservé pour la réactivation), fiche d'annuaire publique dépubliée par \
          événement, e-mail au praticien envoyé de façon asynchrone après l'enregistrement. Le \
          praticien peut alors déposer un recours.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Compte suspendu."),
    @ApiResponse(
        responseCode = "400",
        description =
            "MALFORMED_REQUEST : corps absent ou JSON illisible (code propre à ce module).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ADMIN_PRACTITIONER_NOT_FOUND : aucun compte avec cet identifiant.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "ADMIN_SELF_SUSPEND : l'administrateur vise son propre compte ;"
                + " ADMIN_ACCOUNT_NOT_SUSPENDABLE : le compte est déjà suspended, rejected ou"
                + " closed ; ADMIN_LAST_ADMIN : la cible est le dernier administrateur actif.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ : REQUIRED,"
                + " TOO_SHORT (moins de 20 caractères), TOO_LONG (plus de 4000 caractères) sur le"
                + " champ reason.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  SuspendDto suspend(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @Parameter(
              description = "Identifiant du compte à suspendre.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @RequestBody ReasonRequest body,
      HttpServletRequest request) {
    return service.suspend(admin, id, body.reason(), request.getRemoteAddr());
  }

  @PostMapping("/{id}/reactivate")
  @Operation(
      operationId = "reactivateAdminPractitioner",
      summary = "Réactive un compte suspendu ou refusé avec un motif obligatoire",
      description =
          """
          Administrateur avec 2FA. Le motif (20 à 4000 caractères) est contrôlé en premier. \
          Seuls les comptes suspended ou rejected sont réactivables. Le compte retrouve le statut \
          qu'il avait avant sa suspension (à défaut approved s'il a déjà été approuvé, sinon \
          docs_pending) ; un dossier refusé revient à docs_pending (il doit être redéposé). La date de suspension et la purge planifiée sont effacées. Décision \
          reactivated et ligne admin.reactivate au journal ; e-mail au praticien envoyé de façon \
          asynchrone après l'enregistrement.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Compte réactivé, avec son nouveau statut."),
    @ApiResponse(
        responseCode = "400",
        description =
            "MALFORMED_REQUEST : corps absent ou JSON illisible (code propre à ce module).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ADMIN_PRACTITIONER_NOT_FOUND : aucun compte avec cet identifiant.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "ADMIN_ACCOUNT_NOT_REACTIVABLE : le compte n'est ni suspended ni rejected.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ : REQUIRED,"
                + " TOO_SHORT (moins de 20 caractères), TOO_LONG (plus de 4000 caractères) sur le"
                + " champ reason.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ReactivateDto reactivate(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @Parameter(
              description = "Identifiant du compte à réactiver.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @RequestBody ReasonRequest body,
      HttpServletRequest request) {
    return service.reactivate(admin, id, body.reason(), request.getRemoteAddr());
  }
}
