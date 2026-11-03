package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Connexion précédente du praticien (antérieure à la session courante).")
public record LastLoginResponse(
    @Schema(description = "Date de la connexion (UTC).") Instant at,
    @Schema(
            description =
                "Appareil déduit de l'User-Agent, par exemple « Firefox · Linux ». null pour une"
                    + " connexion antérieure à cette fonction ou dont l'en-tête n'est pas"
                    + " reconnu.",
            example = "Firefox · Linux")
        String device,
    @Schema(
            description =
                "Ville déduite de l'adresse IP. Non renseignée en V1 : aucune géolocalisation,"
                    + " toujours null.")
        String city) {}
