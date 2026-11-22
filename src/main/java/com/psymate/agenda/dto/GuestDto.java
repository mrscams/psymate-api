package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Invité d'un rendez-vous (G Q60), tel que l'affiche la carte « Premier contact ». */
@Schema(
    description =
        "Invité du rendez-vous : personne sans fiche patient (G Q60). Après la purge (6 mois après"
            + " la fin ou l'annulation du rendez-vous ; 30 jours après l'annulation d'un"
            + " rendez-vous en ligne qui n'a pas eu lieu), firstName, lastName, phone et email sont"
            + " null et purgedAt est renseigné.")
public record GuestDto(
    @Schema(description = "Prénom. Null si inconnu ou purgé.", example = "Sara") String firstName,
    @Schema(description = "Nom. Null seulement après la purge.", example = "Benali")
        String lastName,
    @Schema(description = "Téléphone. Null si inconnu ou purgé.", example = "+33 6 12 34 56 78")
        String phone,
    @Schema(description = "E-mail. Null si inconnu ou purgé.", example = "sara.benali@example.test")
        String email,
    @Schema(description = "Instant de la purge UTC. Null tant que l'invité n'est pas purgé.")
        Instant purgedAt) {}
