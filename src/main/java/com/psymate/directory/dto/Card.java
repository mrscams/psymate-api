package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Fiche résumée d'un praticien publié dans les résultats de recherche (sans coordonnées).")
public record Card(
    @Schema(
            description = "Adresse publique du praticien, à passer à la fiche détaillée.",
            example = "claire-lambert")
        String slug,
    @Schema(description = "Nom affiché.", example = "Claire Lambert") String displayName,
    @Schema(
            description = "Profession : psychologist, psychiatrist ou coach.",
            example = "psychologist")
        String profession,
    @Schema(
            description = "Intitulé libre affiché sous le nom ; null si vide.",
            example = "Psychologue clinicienne")
        String titleLabel,
    @Schema(
            description =
                "true si le dossier du praticien est approuvé par l'administration (badge"
                    + " vérifié).")
        boolean verified,
    @Schema(
            description =
                "URL de la photo ; null (aucune route publique ne sert encore les photos).")
        String photoUrl,
    @Schema(
            description =
                "Début de la présentation, 200 caractères au plus, coupé au dernier mot et suivi"
                    + " de « … » s'il est tronqué ; null si le praticien n'en a pas.")
        String bioExcerpt,
    @Schema(
            description =
                "Codes de spécialités (référentiel GET /api/vocabulary/directory). Liste vide"
                    + " si aucune.")
        List<String> specialties,
    @Schema(
            description =
                "Codes de publics accueillis (référentiel GET /api/vocabulary/directory). Liste"
                    + " vide si aucun.")
        List<String> audiences,
    @Schema(
            description =
                "Modalités de consultation : in_person, video, home. Liste vide si aucune.")
        List<String> modalities,
    @Schema(description = "Langues de consultation, codes ISO 639-1. Liste vide si aucune.")
        List<String> languages,
    @Schema(description = "Ville ; null si non renseignée.", example = "Lyon") String city,
    @Schema(
            description =
                "Disponibilité : accepting (accepte de nouveaux patients), waitlist (liste"
                    + " d'attente), closed (complet), unknown (non renseignée).",
            allowableValues = {"accepting", "waitlist", "closed", "unknown"},
            example = "accepting")
        String availability,
    @Schema(
            description =
                "Distance en kilomètres au point lat/lng de la recherche, arrondie au dixième ;"
                    + " null sans lat et lng.",
            example = "3.4")
        Double distanceKm) {}
