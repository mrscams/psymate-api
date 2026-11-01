package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminDtos.DecisionDto;
import com.psymate.identity.admin.AdminDtos.DecisionRequest;
import com.psymate.identity.admin.AdminDtos.DocumentDto;
import com.psymate.identity.admin.AdminDtos.DossierDto;
import com.psymate.identity.admin.AdminDtos.QueueDto;
import com.psymate.identity.admin.AdminDtos.ReviewRequest;
import com.psymate.identity.admin.AdminDtos.SignedUrlDto;
import com.psymate.identity.api.CurrentPractitioner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Validation des dossiers professionnels par l'administration (A10.4, fiche 20). Le rôle admin et
 * la 2FA sont contrôlés en amont par AdminTwoFactorFilter sur tout /api/admin/**.
 */
@Tag(
    name = "Administration : vérifications",
    description =
        "Validation des dossiers professionnels des praticiens par l'administration : file des"
            + " dossiers à traiter, dossier détaillé, ouverture des justificatifs par URL signée,"
            + " qualification des pièces et décision. Réservé aux administrateurs avec"
            + " double authentification active.")
@RestController
@RequestMapping("/api/admin/verifications")
class AdminVerificationController {

  private final AdminVerificationService service;

  AdminVerificationController(AdminVerificationService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listAdminVerifications",
      summary = "Liste la file des dossiers de vérification à traiter",
      description =
          """
          Administrateur avec 2FA. Renvoie les dossiers de praticiens (jamais les administrateurs) \
          dans l'état demandé : under_review (soumis, en attente de décision, par défaut) ou \
          needs_more (complément demandé). Triés par date de soumission, à défaut par dernière \
          mise à jour du compte : du plus ancien (oldest, par défaut) au plus récent (newest). \
          overdue signale un dossier under_review en attente depuis plus de 2 jours ouvrés. \
          Pagination par curseur opaque : limit 50 par défaut, bornée à 1..200 sans erreur ; \
          nextCursor est null sur la dernière page.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Page de la file."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ :"
                + " INVALID_STATUS (status, autre que under_review ou needs_more), INVALID_SORT"
                + " (sort, autre que oldest ou newest), INVALID_CURSOR (cursor illisible).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  QueueDto list(
      @Parameter(
              description =
                  "Statut des dossiers à lister : under_review (défaut) ou needs_more. Autre"
                      + " valeur : 422 INVALID_STATUS.",
              example = "under_review")
          @RequestParam(required = false)
          String status,
      @Parameter(
              description =
                  "Ordre de tri par date de soumission : oldest (défaut, du plus ancien au plus"
                      + " récent) ou newest. Autre valeur : 422 INVALID_SORT.",
              example = "oldest")
          @RequestParam(required = false)
          String sort,
      @Parameter(
              description =
                  "Curseur opaque (nextCursor de la page précédente, à renvoyer tel quel) ;"
                      + " absent pour la première page. Illisible : 422 INVALID_CURSOR.")
          @RequestParam(required = false)
          String cursor,
      @Parameter(
              description =
                  "Nombre maximal de dossiers par page : 50 par défaut, ramené dans l'intervalle"
                      + " 1 à 200 sans erreur.",
              example = "50")
          @RequestParam(required = false)
          Integer limit) {
    return service.list(status, sort, cursor, limit);
  }

  @GetMapping("/{practitionerId}")
  @Operation(
      operationId = "getAdminVerification",
      summary = "Affiche le dossier de vérification d'un praticien",
      description =
          """
          Administrateur avec 2FA. Renvoie l'identité, les coordonnées, les justificatifs non \
          retirés par le praticien (métadonnées seulement, le contenu s'ouvre par URL signée) et \
          l'historique des décisions, quel que soit l'état de vérification du compte. Les \
          comptes administrateurs n'ont pas de dossier (404). Aucune donnée patient.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Dossier du praticien."),
    @ApiResponse(
        responseCode = "404",
        description =
            "ADMIN_DOSSIER_NOT_FOUND : aucun praticien avec cet identifiant, ou compte"
                + " administrateur.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  DossierDto get(
      @Parameter(
              description = "Identifiant du praticien.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID practitionerId) {
    return service.get(practitionerId);
  }

  @GetMapping("/{practitionerId}/documents/{docId}/url")
  @Operation(
      operationId = "getAdminDocumentUrl",
      summary = "Émet une URL signée de 5 minutes pour ouvrir un justificatif",
      description =
          """
          Administrateur avec 2FA. Renvoie un chemin relatif vers getAdminDocumentFile, signé pour \
          cet administrateur et ce justificatif, valable 5 minutes (expiresAt). Rien n'est \
          journalisé à l'émission : c'est l'ouverture du fichier qui est consignée. Le \
          justificatif doit appartenir au praticien, ne pas avoir été retiré par lui (404) ni \
          avoir été purgé (410).""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "URL signée et son échéance."),
    @ApiResponse(
        responseCode = "404",
        description =
            "ADMIN_DOCUMENT_NOT_FOUND : justificatif inexistant, retiré par le praticien ou"
                + " rattaché à un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "410",
        description = "ADMIN_DOCUMENT_PURGED : le fichier a été purgé, il n'existe plus.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  SignedUrlDto getDocumentUrl(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @Parameter(
              description = "Identifiant du praticien propriétaire du justificatif.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID practitionerId,
      @Parameter(
              description = "Identifiant du justificatif.",
              example = "4d8f1e52-7a3b-4c19-8e60-1f2a3b4c5d6e")
          @PathVariable
          UUID docId) {
    return service.signedUrl(admin.id(), practitionerId, docId);
  }

  @GetMapping("/{practitionerId}/documents/{docId}/file")
  @Operation(
      operationId = "getAdminDocumentFile",
      summary = "Sert le justificatif déchiffré et consigne l'accès",
      description =
          """
          Administrateur avec 2FA, le même que celui qui a obtenu l'URL signée : exp et sig \
          viennent de getAdminDocumentUrl et ne sont valables que 5 minutes. Le fichier est \
          déchiffré à la volée et servi en ligne (Content-Disposition inline, Cache-Control \
          no-store, X-Content-Type-Options nosniff) avec son type MIME d'origine. Chaque lecture \
          écrit une ligne dans le journal d'accès aux documents et une ligne admin.document_read \
          dans le journal d'administration.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Contenu du justificatif, avec son type MIME d'origine.",
        headers = {
          @Header(name = "Cache-Control", description = "no-store : ne jamais mettre en cache."),
          @Header(name = "Content-Disposition", description = "inline."),
          @Header(name = "X-Content-Type-Options", description = "nosniff.")
        },
        content = {
          @Content(
              mediaType = "application/pdf",
              schema = @Schema(type = "string", format = "binary")),
          @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
          @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))
        }),
    @ApiResponse(
        responseCode = "403",
        description =
            "ADMIN_SIGNED_URL_INVALID : exp ou sig absent, signature invalide, URL expirée ou"
                + " émise pour un autre administrateur.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "ADMIN_DOCUMENT_NOT_FOUND : justificatif inexistant, retiré par le praticien ou"
                + " rattaché à un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "410",
        description = "ADMIN_DOCUMENT_PURGED : le fichier a été purgé, il n'existe plus.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<byte[]> getDocumentFile(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @Parameter(
              description = "Identifiant du praticien propriétaire du justificatif.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID practitionerId,
      @Parameter(
              description = "Identifiant du justificatif.",
              example = "4d8f1e52-7a3b-4c19-8e60-1f2a3b4c5d6e")
          @PathVariable
          UUID docId,
      @Parameter(
              description =
                  "Échéance de l'URL signée, en secondes depuis l'epoch Unix (UTC), telle que"
                      + " reçue dans l'url de getAdminDocumentUrl. Absent ou dépassé : 403"
                      + " ADMIN_SIGNED_URL_INVALID.",
              example = "1773564900")
          @RequestParam(required = false)
          Long exp,
      @Parameter(
              description =
                  "Signature de l'URL, telle que reçue dans l'url de getAdminDocumentUrl. Absente"
                      + " ou invalide : 403 ADMIN_SIGNED_URL_INVALID.")
          @RequestParam(required = false)
          String sig,
      HttpServletRequest request) {
    AdminVerificationService.FileContent f =
        service.file(admin.id(), practitionerId, docId, exp, sig, request.getRemoteAddr());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(f.mime()))
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .header("X-Content-Type-Options", "nosniff")
        .body(f.bytes());
  }

  @PatchMapping("/{practitionerId}/documents/{docId}")
  @Operation(
      operationId = "reviewAdminDocument",
      summary = "Qualifie un justificatif du dossier",
      description =
          """
          Administrateur avec 2FA. Pose l'état d'examen du justificatif : pending (à examiner), \
          conform ou non_conform ; l'administrateur et la date de qualification sont enregistrés. \
          Réponse 200 avec le justificatif à jour. Aucune restriction sur l'état de vérification \
          du compte, et l'opération peut être répétée. La qualification n'envoie aucun e-mail : \
          elle sert à la décision (approved exige que tous les justificatifs soient conformes, \
          sauf force).""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Justificatif à jour."),
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
        description =
            "ADMIN_DOCUMENT_NOT_FOUND : justificatif inexistant, retiré par le praticien ou"
                + " rattaché à un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; code de champ :"
                + " INVALID_REVIEWED_STATUS (reviewedStatus, absent ou autre que pending,"
                + " conform, non_conform). Contrôlé avant la recherche du justificatif.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  DocumentDto reviewDocument(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @Parameter(
              description = "Identifiant du praticien propriétaire du justificatif.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID practitionerId,
      @Parameter(
              description = "Identifiant du justificatif.",
              example = "4d8f1e52-7a3b-4c19-8e60-1f2a3b4c5d6e")
          @PathVariable
          UUID docId,
      @RequestBody ReviewRequest body) {
    return service.review(admin.id(), practitionerId, docId, body.reviewedStatus());
  }

  @PostMapping("/{practitionerId}/decision")
  @Operation(
      operationId = "decideAdminVerification",
      summary = "Approuve, demande un complément ou refuse un dossier",
      description =
          """
          Administrateur avec 2FA. Décision append-only sur un compte en under_review ou needs_more \
          (409 sinon) : approved passe le compte à approved, needs_more à needs_more, rejected à \
          rejected (compte désactivé, sessions révoquées, purge du compte planifiée 30 jours plus \
          tard). approved exige que tous les justificatifs non purgés soient conformes, sauf \
          force=true avec une note interne. Le praticien reçoit un e-mail, envoyé de façon \
          asynchrone après l'enregistrement ; les fichiers du dossier sont purgés 90 jours après \
          la décision. Réponse 201 sans en-tête Location.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Décision enregistrée, avec le nouvel état de vérification du compte."),
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
        description =
            "ADMIN_DOSSIER_NOT_FOUND : aucun praticien avec cet identifiant, ou compte"
                + " administrateur.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "ADMIN_DOSSIER_NOT_REVIEWABLE : le compte n'est ni under_review ni needs_more ;"
                + " ADMIN_APPROVAL_INCOMPLETE : approved sans force alors que des justificatifs ne"
                + " sont pas conformes ou qu'il n'y en a aucun, la liste des identifiants à"
                + " traiter est dans la propriété complémentaire pendingDocuments.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ :"
                + " INVALID_KIND (kind), MESSAGE_REQUIRED (messageToPractitioner, pour needs_more"
                + " et rejected), TOO_LONG (messageToPractitioner ou internalNote, plus de 4000"
                + " caractères), NOTE_REQUIRED (internalNote, pour approved avec force=true),"
                + " TOO_LARGE (checklist, plus de 20 000 caractères sérialisée). Contrôlé avant la"
                + " recherche du compte.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<DecisionDto> decide(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @Parameter(
              description = "Identifiant du praticien dont le dossier est décidé.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID practitionerId,
      @RequestBody DecisionRequest body,
      HttpServletRequest request) {
    DecisionDto created = service.decide(admin, practitionerId, body, request.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }
}
