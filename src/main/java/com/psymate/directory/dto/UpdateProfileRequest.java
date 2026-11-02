package com.psymate.directory.dto;

import com.psymate.directory.model.ProfileEdit;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * Corps de {@code PUT /api/me/profile} : remplace toutes les colonnes éditables. Les champs absents
 * prennent leur valeur par défaut (listes vides, interrupteurs à faux, disponibilité inconnue).
 * {@code published}, {@code slug} et {@code photo} ne s'éditent pas ici.
 */
@Schema(
    description =
        "Contenu complet du profil d'annuaire : remplace toutes les colonnes éditables, un champ"
            + " absent reprend sa valeur par défaut. published, slug, profession et photo ne"
            + " s'envoient pas.")
public record UpdateProfileRequest(
    @Schema(
            description = "Nom affiché dans l'annuaire, obligatoire, 120 caractères au plus.",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "Claire Lambert")
        String displayName,
    @Schema(
            description =
                "Intitulé libre affiché sous le nom, 120 caractères au plus. Défaut : aucun.",
            maxLength = 120,
            example = "Psychologue clinicienne")
        String titleLabel,
    @Schema(
            description =
                "true pour afficher publiquement le numéro professionnel du compte. Défaut :"
                    + " false.")
        Boolean showProfessionalNumber,
    @Schema(
            description = "Présentation libre, 1 200 caractères au plus. Défaut : aucune.",
            maxLength = 1200)
        String bio,
    @Schema(
            description =
                "Codes de spécialités actifs de GET /api/vocabulary/directory, sans doublon, 8 au"
                    + " plus. Défaut : liste vide.")
        List<String> specialties,
    @Schema(
            description =
                "Codes de publics actifs de GET /api/vocabulary/directory, sans doublon. Défaut :"
                    + " liste vide.")
        List<String> audiences,
    @Schema(
            description =
                "Modalités parmi in_person (au cabinet), video, home (à domicile), sans doublon."
                    + " Défaut : liste vide.")
        List<String> modalities,
    @Schema(
            description =
                "Langues de consultation, codes ISO 639-1 (fr, en…), sans doublon. Défaut : liste"
                    + " vide.")
        List<String> languages,
    @Schema(
            description = "Ville, 100 caractères au plus. Requise pour publier.",
            maxLength = 100,
            example = "Lyon")
        String city,
    @Schema(description = "Code postal, 20 caractères au plus.", maxLength = 20, example = "69003")
        String postalCode,
    @Schema(
            description =
                "Code pays ISO 3166-1 alpha-2 (deux lettres, converti en majuscules). Défaut : FR.",
            example = "FR")
        String country,
    @Schema(
            description = "Téléphone de contact, 30 caractères au plus.",
            maxLength = 30,
            example = "+33 4 72 00 00 00")
        String contactPhone,
    @Schema(
            description = "E-mail de contact public, 254 caractères au plus, de la forme x@y.",
            maxLength = 254,
            example = "contact@cabinet.example")
        String contactEmail,
    @Schema(
            description =
                "Site web, adresse http ou https avec nom d'hôte, 300 caractères au plus.",
            maxLength = 300,
            example = "https://cabinet.example")
        String websiteUrl,
    @Schema(description = "Adresse postale du cabinet, 300 caractères au plus.", maxLength = 300)
        String address,
    @Schema(description = "true pour afficher publiquement contactPhone. Défaut : false.")
        Boolean showPhone,
    @Schema(description = "true pour afficher publiquement contactEmail. Défaut : false.")
        Boolean showEmail,
    @Schema(description = "true pour afficher publiquement address. Défaut : false.")
        Boolean showAddress,
    @Schema(
            description = "Délai de réponse annoncé, 60 caractères au plus.",
            maxLength = 60,
            example = "sous 48 h")
        String responseDelay,
    @Schema(description = "Tarifs, texte libre de 500 caractères au plus.", maxLength = 500)
        String fees,
    @Schema(
            description =
                "Disponibilité : accepting (accepte de nouveaux patients), waitlist (liste"
                    + " d'attente), closed (complet), unknown (non renseignée). Défaut : unknown.",
            allowableValues = {"accepting", "waitlist", "closed", "unknown"},
            example = "accepting")
        String availability,
    @Schema(
            description = "Date du prochain créneau disponible, AAAA-MM-JJ. Défaut : aucune.",
            example = "2026-11-20")
        LocalDate nextSlotAt)
    implements ProfileEdit {

  @Override
  public List<String> specialtiesOrEmpty() {
    return specialties == null ? List.of() : specialties;
  }

  @Override
  public List<String> audiencesOrEmpty() {
    return audiences == null ? List.of() : audiences;
  }

  @Override
  public List<String> modalitiesOrEmpty() {
    return modalities == null ? List.of() : modalities;
  }

  @Override
  public List<String> languagesOrEmpty() {
    return languages == null ? List.of() : languages;
  }
}
