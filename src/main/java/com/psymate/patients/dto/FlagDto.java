package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(
    description =
        "Drapeau actif posé sur un patient (point d'attention clinique). Un drapeau levé n'est"
            + " plus renvoyé.")
public record FlagDto(
    @Schema(
            description =
                "Nature du drapeau : watch = à surveiller (une raison est alors obligatoire),"
                    + " risk = risque, shared_care = suivi partagé avec un autre professionnel.",
            allowableValues = {"watch", "risk", "shared_care"},
            example = "watch")
        String kind,
    @Schema(
            description =
                "Raison saisie par le praticien ; toujours renseignée pour watch, null pour risk"
                    + " et shared_care quand aucune raison n'a été saisie.",
            example = "Troubles du sommeil depuis trois semaines")
        String reason,
    @Schema(
            description = "Date-heure de pose du drapeau, UTC ISO 8601.",
            example = "2026-03-14T09:30:00Z")
        OffsetDateTime setAt) {}
