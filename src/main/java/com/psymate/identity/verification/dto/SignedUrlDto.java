package com.psymate.identity.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
    description =
        "URL signée, à courte durée, pour lire un justificatif déchiffré. Elle ne fonctionne"
            + " qu'avec la session du praticien propriétaire.")
public record SignedUrlDto(
    @Schema(
            description =
                "Chemin relatif à appeler en GET (avec le cookie de session) : il porte les"
                    + " paramètres exp et sig. Valable 5 minutes.",
            example =
                "/api/me/verification/documents/9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e/file?exp=1773480600&sig=x7F2k9Qw0m5T3bYh8LrN1cVdE6aGsUoJpZiXtKyA4Hc")
        String url,
    @Schema(
            description = "Date-heure d'expiration de l'URL, en UTC (5 minutes après l'émission).",
            example = "2026-03-14T09:35:00Z")
        Instant expiresAt) {}
