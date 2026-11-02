package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
    name = "AdminDocumentDto",
    description =
        "Justificatif du dossier de vérification d'un praticien. Le contenu ne s'ouvre que par"
            + " une URL signée (getAdminDocumentUrl).")
public record DocumentDto(
    @Schema(
            description = "Identifiant du justificatif.",
            example = "4d8f1e52-7a3b-4c19-8e60-1f2a3b4c5d6e")
        UUID id,
    @Schema(
            description = "Nature de la pièce.",
            allowableValues = {
              "diploma",
              "registration",
              "certification",
              "professional_card",
              "identity",
              "other"
            })
        String type,
    @Schema(description = "Intitulé donné par le praticien.", example = "Master de psychologie")
        String title,
    @Schema(
            description = "Organisme émetteur ; null si non renseigné.",
            example = "Université Lyon 2")
        String issuer,
    @Schema(
            description = "Année d'obtention (1950 à 2100) ; null si non renseignée.",
            example = "2014")
        Integer year,
    @Schema(
            description = "Type MIME du fichier.",
            allowableValues = {"application/pdf", "image/jpeg", "image/png"})
        String mime,
    @Schema(description = "Taille du fichier en octets (10 Mo au plus).", example = "482113")
        int sizeBytes,
    @Schema(description = "Date-heure UTC (ISO 8601) du dépôt.", example = "2026-03-14T09:12:00Z")
        Instant uploadedAt,
    @Schema(
            description =
                "Qualification par l'administration : pending (pas encore examiné), conform ou"
                    + " non_conform.",
            allowableValues = {"pending", "conform", "non_conform"})
        String reviewedStatus,
    @Schema(
            description =
                "Date-heure UTC (ISO 8601) de la dernière qualification ; null tant que le"
                    + " justificatif n'a pas été qualifié.",
            example = "2026-03-14T10:05:00Z")
        Instant reviewedAt,
    @Schema(
            description =
                "Vrai si le fichier a été purgé (90 jours après la décision) : il n'est plus"
                    + " téléchargeable (410 ADMIN_DOCUMENT_PURGED), seules les métadonnées"
                    + " restent.")
        boolean purged) {}
