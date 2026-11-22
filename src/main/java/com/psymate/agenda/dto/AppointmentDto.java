package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Rendez-vous exposé par l'API (I3) : détail du panneau droit de la fiche 15. */
@Schema(description = "Rendez-vous du praticien. Dates UTC ISO 8601.")
public record AppointmentDto(
    @Schema(description = "Identifiant du rendez-vous.") UUID id,
    @Schema(description = "Patient du rendez-vous. Null pour un rendez-vous invité.")
        AppointmentPatientDto patient,
    @Schema(
            description =
                "Invité du rendez-vous (G Q60) : personne sans fiche patient. Null pour un"
                    + " rendez-vous patient.")
        GuestDto guest,
    @Schema(description = "Série de répétition. Null pour un rendez-vous isolé.") UUID seriesId,
    @Schema(description = "Début du rendez-vous, UTC ISO 8601.", example = "2026-11-16T09:00:00Z")
        Instant startsAt,
    @Schema(description = "Fin du rendez-vous, UTC ISO 8601.", example = "2026-11-16T09:50:00Z")
        Instant endsAt,
    @Schema(description = "Durée en minutes (fin moins début).", example = "50") int durationMin,
    @Schema(
            description = "Modalité du rendez-vous.",
            allowableValues = {"in_person", "video", "phone"})
        String modality,
    @Schema(
            description =
                "Statut : planned (planifié), tentative (à confirmer), cancelled (annulé), done"
                    + " (séance rattachée).",
            allowableValues = {"planned", "tentative", "cancelled", "done"})
        String status,
    @Schema(
            description =
                "Origine (G Q74) : practitioner (posé par le praticien, seule valeur d'un"
                    + " rendez-vous créé par POST /api/appointments) ou online (demande en ligne"
                    + " d'un visiteur). Fixée par le serveur, jamais modifiable.",
            allowableValues = {"practitioner", "online"},
            example = "practitioner")
        String origin,
    @Schema(description = "Note de planification, visible seulement dans l'agenda. Null si aucune.")
        String planningNote,
    @Schema(description = "Motif d'annulation. Null si aucun.") String cancelReason,
    @Schema(description = "Instant d'annulation UTC. Null si le rendez-vous n'est pas annulé.")
        Instant cancelledAt,
    @Schema(description = "Séance rattachée (entrée de type session). Null sinon.")
        UUID sessionEntryId,
    @Schema(description = "Instant de création UTC.") Instant createdAt,
    @Schema(description = "Instant de dernière modification UTC.") Instant updatedAt) {}
