package com.psymate.identity.verification.controller;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.verification.dto.DocumentDto;
import com.psymate.identity.verification.dto.ProfessionalInfoDto;
import com.psymate.identity.verification.dto.ProfessionalInfoRequest;
import com.psymate.identity.verification.dto.SignedUrlDto;
import com.psymate.identity.verification.dto.VerificationDto;
import com.psymate.identity.verification.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Dossier professionnel du praticien connecté (A10.4, fiche 13). */
@Tag(
    name = "Vérification du praticien",
    description =
        "Dossier professionnel du praticien connecté : justificatifs, informations"
            + " professionnelles, soumission du dossier à l'examen de l'administration.")
@RestController
@RequestMapping("/api/me")
class VerificationController {

  private final VerificationService service;

  VerificationController(VerificationService service) {
    this.service = service;
  }

  @GetMapping("/verification")
  @Operation(
      summary = "État du dossier professionnel, justificatifs, pièces manquantes",
      description =
          """
          Session praticien (compte confirmé). Renvoie l'état de la vérification \
          (email_pending, docs_pending, under_review, approved, needs_more, rejected, suspended, \
          closed), les justificatifs actifs, les types obligatoires manquants (missing) et \
          conseillés (recommended), les décisions reçues avec leur message et les informations \
          professionnelles. La note interne de l'administrateur n'est jamais exposée.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Dossier professionnel du praticien."),
    @ApiResponse(
        responseCode = "404",
        description = "ACCOUNT_NOT_FOUND : compte du praticien introuvable.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  VerificationDto getVerification(@AuthenticationPrincipal CurrentPractitioner me) {
    return service.get(me.id());
  }

  @PostMapping(path = "/verification/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Dépose un justificatif (PDF, JPEG ou PNG, 10 Mo au maximum)",
      description =
          """
          Session praticien (compte confirmé), requête multipart/form-data : le fichier dans le \
          champ file ; type, title, issuer et year sont lus comme paramètres de requête ou champs \
          du formulaire (équivalents pour le serveur) ; type, title et file sont obligatoires \
          (422 sinon). Autorisé seulement en docs_pending et needs_more ; 20 \
          justificatifs actifs au maximum. Le format est reconnu d'après le contenu du fichier \
          (jamais l'extension) ; les images sont ré-encodées sans métadonnées (EXIF, GPS), les PDF \
          conservés tels quels. Le fichier est stocké chiffré. Limites : 10 Mo par fichier, image \
          de 25 millions de pixels au maximum. Réponse 201 avec le justificatif créé.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Justificatif déposé."),
    @ApiResponse(
        responseCode = "404",
        description = "ACCOUNT_NOT_FOUND : compte du praticien introuvable.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "VERIFICATION_LOCKED : le dossier n'est pas modifiable dans son état actuel (hors"
                + " docs_pending et needs_more) ; DOCUMENT_LIMIT : 20 justificatifs au maximum par"
                + " dossier.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "413",
        description =
            "DOCUMENT_TOO_LARGE : le fichier dépasse 10 Mo (au-delà du plafond global de la"
                + " requête multipart, la réponse est UPLOAD_TOO_LARGE).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "415",
        description = "DOCUMENT_UNSUPPORTED : seuls les fichiers PDF, JPEG et PNG sont acceptés.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} : INVALID_TYPE (type inconnu"
                + " ou absent), INVALID_TITLE (title vide ou de plus de 200 caractères),"
                + " INVALID_ISSUER (issuer de plus de 200 caractères), INVALID_YEAR (year hors de"
                + " 1950 à 2100 ou non numérique) ; DOCUMENT_INVALID : fichier absent ou vide,"
                + " image illisible ou corrompue, image de plus de 25 millions de pixels.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<DocumentDto> uploadDocument(
      @AuthenticationPrincipal CurrentPractitioner me,
      @Parameter(
              description =
                  "Nature du justificatif : diploma, registration, certification,"
                      + " professional_card, identity ou other. Obligatoire.",
              example = "diploma")
          @RequestParam(required = false)
          String type,
      @Parameter(
              description = "Intitulé du justificatif, 200 caractères au maximum. Obligatoire.",
              example = "Master de psychologie clinique")
          @RequestParam(required = false)
          String title,
      @Parameter(
              description =
                  "Établissement ou organisme émetteur, 200 caractères au maximum. Facultatif.",
              example = "Université de Lyon")
          @RequestParam(required = false)
          String issuer,
      @Parameter(
              description = "Année d'obtention, entier de 1950 à 2100. Facultatif.",
              example = "2012")
          @RequestParam(required = false)
          String year,
      @Parameter(
              description =
                  "Fichier du justificatif : PDF, JPEG ou PNG, 10 Mo au maximum. Obligatoire.")
          @RequestParam(required = false)
          MultipartFile file) {
    byte[] content = null;
    if (file != null) {
      try {
        content = file.getBytes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    DocumentDto created = service.upload(me.id(), type, title, issuer, year, content);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/verification/documents/{id}")
  @Operation(
      summary = "Métadonnées d'un justificatif (403 s'il appartient à un autre praticien)",
      description =
          """
          Session praticien (compte confirmé), propriétaire du justificatif. Renvoie les \
          métadonnées, jamais le fichier (voir /url et /file). Un justificatif retiré est \
          introuvable ; un justificatif purgé reste lisible en métadonnées (purged = true).\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Métadonnées du justificatif."),
    @ApiResponse(
        responseCode = "403",
        description = "DOCUMENT_FORBIDDEN : le justificatif appartient à un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "DOCUMENT_NOT_FOUND : justificatif inexistant ou retiré.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  DocumentDto getDocument(
      @AuthenticationPrincipal CurrentPractitioner me,
      @Parameter(
              description = "Identifiant du justificatif.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id) {
    return service.getDocument(me.id(), id);
  }

  @DeleteMapping("/verification/documents/{id}")
  @Operation(
      summary = "Retire un justificatif (docs_pending ou needs_more seulement)",
      description =
          """
          Session praticien (compte confirmé), propriétaire du justificatif. Autorisé seulement \
          en docs_pending et needs_more. Le justificatif disparaît du dossier (suppression \
          logique) et son fichier stocké est effacé.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Justificatif retiré."),
    @ApiResponse(
        responseCode = "403",
        description = "DOCUMENT_FORBIDDEN : le justificatif appartient à un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "DOCUMENT_NOT_FOUND : justificatif inexistant ou déjà retiré ; ACCOUNT_NOT_FOUND :"
                + " compte du praticien introuvable.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "VERIFICATION_LOCKED : le dossier n'est pas modifiable dans son état actuel (hors"
                + " docs_pending et needs_more).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> deleteDocument(
      @AuthenticationPrincipal CurrentPractitioner me,
      @Parameter(
              description = "Identifiant du justificatif.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id) {
    service.delete(me.id(), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/verification/documents/{id}/url")
  @Operation(
      summary = "URL signée de 5 minutes pour lire le justificatif déchiffré",
      description =
          """
          Session praticien (compte confirmé), propriétaire du justificatif. Renvoie un chemin \
          relatif signé (paramètres exp et sig) valable 5 minutes, à appeler en GET avec la même \
          session pour obtenir le fichier déchiffré (voir /file). Un justificatif purgé n'a plus \
          de fichier : 410.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "URL signée et date d'expiration."),
    @ApiResponse(
        responseCode = "403",
        description = "DOCUMENT_FORBIDDEN : le justificatif appartient à un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "DOCUMENT_NOT_FOUND : justificatif inexistant ou retiré.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "410",
        description = "DOCUMENT_PURGED : le fichier a été supprimé du stockage.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  SignedUrlDto getDocumentUrl(
      @AuthenticationPrincipal CurrentPractitioner me,
      @Parameter(
              description = "Identifiant du justificatif.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id) {
    return service.signedUrl(me.id(), id);
  }

  @GetMapping("/verification/documents/{id}/file")
  @Operation(
      summary = "Sert le justificatif déchiffré (URL signée, praticien propriétaire)",
      description =
          """
          Session praticien (compte confirmé), propriétaire du justificatif, avec la signature \
          obtenue par GET .../url (exp et sig, valables 5 minutes). Renvoie le fichier déchiffré \
          dans son type d'origine (PDF, JPEG ou PNG), en Content-Disposition inline, \
          Cache-Control no-store et X-Content-Type-Options nosniff. Chaque lecture est inscrite au \
          journal d'accès aux documents (adresse IP du client).\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Fichier du justificatif (corps binaire).",
        headers = {
          @Header(name = "Cache-Control", description = "no-store."),
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
            "DOCUMENT_FORBIDDEN : le justificatif appartient à un autre praticien ;"
                + " SIGNED_URL_INVALID : exp ou sig absent, signature incorrecte ou URL expirée.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "DOCUMENT_NOT_FOUND : justificatif inexistant ou retiré.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "410",
        description = "DOCUMENT_PURGED : le fichier a été supprimé du stockage.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<byte[]> getDocumentFile(
      @AuthenticationPrincipal CurrentPractitioner me,
      @Parameter(
              description = "Identifiant du justificatif.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @Parameter(
              description =
                  "Échéance de la signature, en secondes depuis l'epoch Unix (UTC), telle que"
                      + " renvoyée dans l'url de GET .../url. Sa valeur est signée : ne pas la"
                      + " modifier.",
              example = "1773480600")
          @RequestParam(required = false)
          Long exp,
      @Parameter(
              description = "Signature de l'URL, telle que renvoyée dans l'url de GET .../url.",
              example = "x7F2k9Qw0m5T3bYh8LrN1cVdE6aGsUoJpZiXtKyA4Hc")
          @RequestParam(required = false)
          String sig,
      HttpServletRequest request) {
    VerificationService.FileContent f =
        service.file(me.id(), id, exp, sig, request.getRemoteAddr());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(f.mime()))
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .header("X-Content-Type-Options", "nosniff")
        .body(f.bytes());
  }

  @PostMapping("/verification/submit")
  @Operation(
      summary = "Soumet le dossier pour vérification (409 avec les pièces manquantes)",
      description =
          """
          Session praticien (compte confirmé). Autorisé seulement en docs_pending et needs_more, \
          avec un diplôme et une pièce d'identité au moins parmi les justificatifs non purgés ; \
          sinon 409 VERIFICATION_INCOMPLETE avec missing[] (types obligatoires absents). Succès : \
          le dossier passe en under_review, la soumission est consignée et les administrateurs \
          sont notifiés par e-mail. Renvoie le dossier à jour.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Dossier soumis, statut under_review."),
    @ApiResponse(
        responseCode = "404",
        description = "ACCOUNT_NOT_FOUND : compte du praticien introuvable.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "VERIFICATION_LOCKED : le dossier n'est pas soumettable dans son état actuel (hors"
                + " docs_pending et needs_more) ; VERIFICATION_INCOMPLETE : justificatifs"
                + " obligatoires manquants, liste dans missing[] (diploma, identity).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  VerificationDto submitVerification(@AuthenticationPrincipal CurrentPractitioner me) {
    return service.submit(me.id());
  }

  @PatchMapping("/professional-info")
  @Operation(
      summary = "Met à jour n° professionnel, téléphone, adresse et exercice à distance",
      description =
          """
          Session praticien (compte confirmé). Mise à jour partielle : un champ absent ou null \
          est inchangé, une chaîne vide l'efface. Possible quel que soit l'état du dossier. Aucune \
          modification n'est appliquée si un champ est invalide. Renvoie les informations \
          enregistrées.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Informations professionnelles à jour."),
    @ApiResponse(
        responseCode = "404",
        description = "ACCOUNT_NOT_FOUND : compte du praticien introuvable.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} : INVALID_FIELD, field ="
                + " professionalNumber (4 à 20 caractères : lettres, chiffres, espace, tiret),"
                + " phone (6 à 30 caractères : chiffres, + espace ( ) . tiret) ou address.street,"
                + " address.postalCode, address.city (200 caractères au maximum).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ProfessionalInfoDto updateProfessionalInfo(
      @AuthenticationPrincipal CurrentPractitioner me, @RequestBody ProfessionalInfoRequest r) {
    return service.updateProfessionalInfo(me.id(), r);
  }
}
