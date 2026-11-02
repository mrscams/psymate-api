package com.psymate.identity.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Justificatif : jamais de clé de stockage ni d'empreinte. */
@Schema(
    description =
        "Justificatif déposé dans le dossier professionnel. Aucune clé de stockage ni empreinte"
            + " n'est exposée ; le fichier se lit via GET"
            + " /api/me/verification/documents/{id}/url.")
public record DocumentDto(
    @Schema(description = "Identifiant du justificatif.") UUID id,
    @Schema(
            description =
                "Nature du justificatif : diploma = diplôme, registration = attestation"
                    + " d'inscription (ADELI ou RPPS), certification = certification (coach),"
                    + " professional_card = carte professionnelle, identity = pièce d'identité,"
                    + " other = autre pièce. Un diplôme et une pièce d'identité sont obligatoires"
                    + " pour soumettre le dossier.",
            allowableValues = {
              "diploma",
              "registration",
              "certification",
              "professional_card",
              "identity",
              "other"
            },
            example = "diploma")
        String type,
    @Schema(
            description = "Intitulé saisi par le praticien (200 caractères au maximum).",
            example = "Master de psychologie clinique")
        String title,
    @Schema(
            description = "Établissement ou organisme émetteur ; null si non renseigné.",
            example = "Université de Lyon")
        String issuer,
    @Schema(
            description = "Année d'obtention, de 1950 à 2100 ; null si non renseignée.",
            example = "2012")
        Integer year,
    @Schema(
            description =
                "Type MIME détecté par signature du fichier (jamais d'après l'extension)."
                    + " L'image est ré-encodée sans métadonnées, le PDF est conservé tel quel.",
            allowableValues = {"application/pdf", "image/jpeg", "image/png"},
            example = "application/pdf")
        String mime,
    @Schema(
            description = "Taille du fichier stocké, en octets (10 Mo au maximum).",
            example = "482133")
        int sizeBytes,
    @Schema(description = "Date-heure du dépôt, en UTC.", example = "2026-03-14T09:30:00Z")
        Instant uploadedAt,
    @Schema(
            description =
                "Résultat de l'examen du justificatif par l'administration : pending = pas encore"
                    + " examiné, conform = conforme, non_conform = non conforme.",
            allowableValues = {"pending", "conform", "non_conform"},
            example = "pending")
        String reviewedStatus,
    @Schema(
            description =
                "true si le fichier a été purgé du stockage (90 jours après la décision) : les"
                    + " métadonnées restent, mais l'URL et le fichier répondent 410"
                    + " DOCUMENT_PURGED.")
        boolean purged) {}
