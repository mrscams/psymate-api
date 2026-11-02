package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(
    description =
        "Fiche publique complète d'un praticien publié. Les coordonnées (phone, email, address)"
            + " et professionalNumber sont null si le praticien ne les affiche pas.")
public record PublicProfile(
    @Schema(description = "Adresse publique du praticien.", example = "claire-lambert") String slug,
    @Schema(description = "Nom affiché.", example = "Claire Lambert") String displayName,
    @Schema(
            description = "Profession : psychologist, psychiatrist ou coach.",
            example = "psychologist")
        String profession,
    @Schema(
            description =
                "true si le dossier du praticien est approuvé par l'administration (badge"
                    + " vérifié).")
        boolean verified,
    @Schema(
            description = "Intitulé libre affiché sous le nom ; null si vide.",
            example = "Psychologue clinicienne")
        String titleLabel,
    @Schema(
            description =
                "Numéro professionnel ; null si le praticien ne l'affiche pas (ou n'en a pas).")
        String professionalNumber,
    @Schema(
            description =
                "URL de la photo ; null (aucune route publique ne sert encore les photos).")
        String photoUrl,
    @Schema(description = "Présentation complète, 1 200 caractères au plus ; null si vide.")
        String bio,
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
    @Schema(description = "Code postal ; null si non renseigné.", example = "69003")
        String postalCode,
    @Schema(description = "Code pays ISO 3166-1 alpha-2.", example = "FR") String country,
    @Schema(
            description = "Téléphone ; null si le praticien ne l'affiche pas.",
            example = "+33 4 72 00 00 00")
        String phone,
    @Schema(
            description = "E-mail de contact ; null si le praticien ne l'affiche pas.",
            example = "contact@cabinet.example")
        String email,
    @Schema(description = "Adresse postale du cabinet ; null si le praticien ne l'affiche pas.")
        String address,
    @Schema(description = "Site web ; null si non renseigné.", example = "https://cabinet.example")
        String websiteUrl,
    @Schema(
            description = "Délai de réponse annoncé ; null si non renseigné.",
            example = "sous 48 h")
        String responseDelay,
    @Schema(description = "Tarifs, texte libre ; null si non renseignés.") String fees,
    @Schema(
            description =
                "Disponibilité : accepting (accepte de nouveaux patients), waitlist (liste"
                    + " d'attente), closed (complet), unknown (non renseignée).",
            allowableValues = {"accepting", "waitlist", "closed", "unknown"},
            example = "accepting")
        String availability,
    @Schema(
            description = "Date du prochain créneau disponible, AAAA-MM-JJ ; null si inconnue.",
            example = "2026-11-20")
        LocalDate nextSlotAt,
    @Schema(
            description = "Date de publication du profil, ISO 8601 UTC.",
            example = "2026-10-12T14:30:00Z")
        OffsetDateTime publishedAt) {}
