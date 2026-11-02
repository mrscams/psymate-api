package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
    name = "AdminSignedUrlDto",
    description = "URL signée et éphémère pour ouvrir un justificatif.")
public record SignedUrlDto(
    @Schema(
            description =
                "Chemin relatif de l'API (getAdminDocumentFile) avec exp et sig en paramètres de"
                    + " requête ; valable uniquement pour l'administrateur qui l'a demandée.",
            example =
                "/api/admin/verifications/9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e/documents/4d8f1e52-7a3b-4c19-8e60-1f2a3b4c5d6e/file?exp=1773564900&sig=Zm9v")
        String url,
    @Schema(
            description = "Date-heure UTC (ISO 8601) d'expiration, 5 minutes après l'émission.",
            example = "2026-03-15T08:05:00Z")
        Instant expiresAt) {}
