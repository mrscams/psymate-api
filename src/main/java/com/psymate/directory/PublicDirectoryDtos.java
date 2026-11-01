package com.psymate.directory;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** DTO de l'annuaire public (aucune donnée patient, contacts filtrés). */
final class PublicDirectoryDtos {

  private PublicDirectoryDtos() {}

  @Schema(
      description =
          "Fiche résumée d'un praticien publié dans les résultats de recherche (sans coordonnées).")
  record Card(
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

  @Schema(description = "Nombre de résultats pour une valeur d'une facette.")
  record FacetCount(
      @Schema(
              description =
                  "Valeur de la facette : code de profession, de modalité, de public ou"
                      + " de langue.",
              example = "video")
          String code,
      @Schema(description = "Nombre de fiches du lot filtré qui ont cette valeur.", example = "12")
          long count) {}

  @Schema(
      description =
          "Facettes calculées sur le lot filtré (500 fiches au plus), chacune triée par nombre"
              + " décroissant.")
  record Facets(
      @Schema(description = "Répartition par profession (psychologist, psychiatrist, coach).")
          List<FacetCount> professions,
      @Schema(description = "Répartition par modalité (in_person, video, home).")
          List<FacetCount> modalities,
      @Schema(description = "Répartition par public accueilli.") List<FacetCount> audiences,
      @Schema(description = "Répartition par langue (ISO 639-1).") List<FacetCount> languages) {}

  @Schema(description = "Page de résultats de la recherche dans l'annuaire public.")
  record SearchResult(
      @Schema(description = "Fiches de la page, au plus 20, dans l'ordre du tri demandé.")
          List<Card> items,
      @Schema(description = "Numéro de la page rendue, à partir de 1.", example = "1") int page,
      @Schema(description = "Taille de page, toujours 20.", example = "20") int pageSize,
      @Schema(
              description =
                  "Nombre total de fiches correspondantes (dans la limite de 500 fiches"
                      + " examinées).",
              example = "37")
          long total,
      @Schema(description = "Nombre total de pages ; 0 s'il n'y a aucun résultat.", example = "2")
          int totalPages,
      @Schema(description = "Facettes du lot filtré, toutes pages confondues.") Facets facets) {}

  @Schema(
      description =
          "Fiche publique complète d'un praticien publié. Les coordonnées (phone, email, address)"
              + " et professionalNumber sont null si le praticien ne les affiche pas.")
  record PublicProfile(
      @Schema(description = "Adresse publique du praticien.", example = "claire-lambert")
          String slug,
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
      @Schema(
              description = "Site web ; null si non renseigné.",
              example = "https://cabinet.example")
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
}
