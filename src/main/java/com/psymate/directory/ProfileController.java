package com.psymate.directory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Profil public du praticien connecté (écran 10). */
@Tag(
    name = "Profil annuaire",
    description =
        "Profil public du praticien connecté dans l'annuaire : édition, photo, publication et"
            + " dépublication.")
@RestController
@RequestMapping("/api/me/profile")
class ProfileController {

  private final ProfileService service;

  ProfileController(ProfileService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Lit le profil d'annuaire du praticien connecté",
      description =
          """
          Session praticien. Le profil est créé vide au premier appel (nom affiché du compte, \
          adresse publique slug unique dérivée du nom, non publié) ; les appels suivants le \
          relisent. La profession vient du compte et n'est pas modifiable ici.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Profil du praticien connecté."),
    @ApiResponse(
        responseCode = "409",
        description =
            "SLUG_CONFLICT : impossible de générer une adresse publique unique à la création du"
                + " profil, réessayer.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ProfileDto get() {
    return service.get();
  }

  @PutMapping
  @Operation(
      summary = "Remplace les champs éditables du profil d'annuaire",
      description =
          """
          Session praticien. Remplace toutes les colonnes éditables : un champ absent reprend sa \
          valeur par défaut (listes vides, interrupteurs show* à false, availability unknown, \
          country FR, autres champs vides). published, slug, profession et la photo ne \
          s'éditent pas ici (champs ignorés). Les listes fermées specialties et audiences \
          n'acceptent que des codes actifs de GET /api/vocabulary/directory. Les textes sont \
          rognés et un texte vide devient null. Limites : displayName 120 (obligatoire), titleLabel \
          120, contactPhone 30, contactEmail 254, websiteUrl 300, address 300, city 100, \
          postalCode 20, responseDelay 60, fees 500, bio 1 200 caractères, 8 spécialités au plus.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Profil mis à jour."),
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_BODY : corps JSON illisible (syntaxe ou type de champ invalide).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "SLUG_CONFLICT : impossible de générer une adresse publique unique à la création du"
                + " profil, réessayer.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, message} (pas de code par champ), un élément"
                + " par violation ; champs : displayName (absent, vide ou de plus de 120"
                + " caractères) ; titleLabel, contactPhone, contactEmail, websiteUrl, address,"
                + " city, postalCode, responseDelay, fees, bio (longueur maximale dépassée) ;"
                + " specialties, audiences, modalities, languages (valeur hors liste, inactive ou"
                + " en double ; specialties : plus de 8 valeurs) ; availability (hors accepting,"
                + " waitlist, closed, unknown) ; websiteUrl (pas une adresse http ou https) ;"
                + " contactEmail (adresse invalide) ; country (pas un code ISO à deux lettres).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ProfileDto update(@RequestBody UpdateProfileRequest request) {
    return service.update(request);
  }

  @PostMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Envoie la photo du profil d'annuaire",
      description =
          """
          Session praticien. Requête multipart/form-data avec la partie file : image JPEG ou PNG \
          (signature vérifiée, pas le Content-Type). Limites : 5 Mo, 25 millions de pixels. La \
          photo est recadrée en carré au centre, réduite à 512 px au plus, réécrite en JPEG sans \
          métadonnées (EXIF et GPS supprimés) et remplace la précédente. Réponse : le profil mis \
          à jour.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Photo enregistrée ; profil mis à jour."),
    @ApiResponse(
        responseCode = "409",
        description =
            "SLUG_CONFLICT : impossible de générer une adresse publique unique à la création du"
                + " profil, réessayer.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "413",
        description = "PHOTO_TOO_LARGE : le fichier dépasse 5 Mo.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "415",
        description = "PHOTO_UNSUPPORTED : le fichier n'est ni un JPEG ni un PNG.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "PHOTO_INVALID : fichier vide, image illisible ou corrompue ;"
                + " PHOTO_TOO_LARGE_DIMENSIONS : image de plus de 25 millions de pixels.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ProfileDto uploadPhoto(
      @Parameter(description = "Image JPEG ou PNG, 5 Mo et 25 millions de pixels au plus.")
          @RequestPart("file")
          MultipartFile file)
      throws IOException {
    return service.uploadPhoto(file.getBytes());
  }

  @DeleteMapping("/photo")
  @Operation(
      summary = "Retire la photo du profil d'annuaire",
      description =
          """
          Session praticien. Supprime la photo du profil ; idempotent : sans photo, répond aussi \
          204 sans effet.""")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Photo retirée (ou déjà absente)."),
    @ApiResponse(
        responseCode = "409",
        description =
            "SLUG_CONFLICT : impossible de générer une adresse publique unique à la création du"
                + " profil, réessayer.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> deletePhoto() {
    service.deletePhoto();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/publish")
  @Operation(
      summary = "Publie le profil dans l'annuaire public",
      description =
          """
          Session praticien. Le dossier de vérification doit être approuvé et le profil complet : \
          nom affiché, profession, ville, et au moins un moyen de contact affiché et renseigné \
          (téléphone, e-mail ou adresse avec son interrupteur show* à true) ; les spécialités et \
          publics enregistrés doivent être encore actifs. Sinon 409 PROFILE_INCOMPLETE avec \
          missing[], sans rien publier. Idempotent : un profil déjà publié reste inchangé. Le \
          profil est dépublié automatiquement quand le dossier est refusé ou le compte suspendu.""")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Profil publié (ou déjà publié)."),
    @ApiResponse(
        responseCode = "409",
        description =
            "PROFILE_INCOMPLETE : conditions de publication non remplies, missing[] liste les"
                + " éléments à fournir dans cet ordre : verification (dossier non approuvé),"
                + " displayName, profession, city, contact (aucun moyen de contact affiché et"
                + " renseigné), specialties ou audiences (code désactivé depuis l'enregistrement) ;"
                + " SLUG_CONFLICT : impossible de générer une adresse publique unique à la création"
                + " du profil, réessayer.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> publish() {
    service.publish();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/unpublish")
  @Operation(
      summary = "Retire le profil de l'annuaire public",
      description =
          """
          Session praticien. Le profil cesse d'apparaître dans l'annuaire public (sa fiche répond \
          404 PROFILE_NOT_FOUND) mais reste éditable ; publishedAt est conservé. Idempotent : un \
          profil non publié reste inchangé.""")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Profil dépublié (ou déjà non publié)."),
    @ApiResponse(
        responseCode = "409",
        description =
            "SLUG_CONFLICT : impossible de générer une adresse publique unique à la création du"
                + " profil, réessayer.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> unpublish() {
    service.unpublish();
    return ResponseEntity.noContent().build();
  }
}
