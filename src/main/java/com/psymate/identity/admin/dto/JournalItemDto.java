package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(
    description =
        "Ligne du journal d'administration : une décision ou un événement de sécurité. Jamais"
            + " d'adresse IP ni de donnée patient.")
public record JournalItemDto(
    @Schema(
            description = "Identifiant de la ligne.",
            example = "1f2e3d4c-5b6a-4978-8695-a4b3c2d1e0f9")
        UUID id,
    @Schema(
            description = "Date-heure UTC (ISO 8601) de l'événement.",
            example = "2026-04-02T10:00:00Z")
        Instant at,
    @Schema(
            description =
                "Type d'événement, en minuscules avec points ou soulignés. Les décisions"
                    + " apparaissent sous admin.<kind> (admin.approved, admin.suspended,"
                    + " admin.appeal_accepted…), les événements de sécurité sous leur propre"
                    + " nom (admin.decision, admin.suspend, admin.reactivate, admin.appeal,"
                    + " admin.document_read, appeal.submitted, verification_submitted,"
                    + " session_revoked, login_failed, locked…).",
            example = "admin.suspend")
        String kind,
    @Schema(
            description =
                "Table d'origine de la ligne : security_event (événement de sécurité) ou"
                    + " verification_decision (décision d'administration).",
            allowableValues = {"security_event", "verification_decision"})
        String source,
    @Schema(
            description =
                "Administrateur auteur de l'acte ; null si l'événement n'est pas l'acte d'un"
                    + " administrateur (praticien lui-même ou système).")
        ActorDto actor,
    @Schema(
            description =
                "Nature de l'auteur : admin (un administrateur a agi), practitioner (le compte"
                    + " concerné lui-même) ou system (ni l'un ni l'autre).",
            allowableValues = {"admin", "practitioner", "system"})
        String actorType,
    @Schema(
            description =
                "Identifiant du compte concerné ; null si l'événement n'est rattaché à aucun"
                    + " compte.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID practitionerId,
    @Schema(
            description = "Nom d'affichage du compte concerné ; null si practitionerId est null.",
            example = "Claire Martin")
        String practitionerDisplayName,
    @Schema(
            description =
                "Résumé lisible : le champ summary des détails s'il existe, sinon le message"
                    + " de la décision, sinon le type d'événement.",
            example = "Compte suspendu : signalements répétés")
        String summary,
    @Schema(
            description =
                "Détails structurés de l'événement (clés et valeurs JSON variables selon kind,"
                    + " par exemple reason, previousStatus, newStatus, decisionId, appealId) ;"
                    + " objet vide s'il n'y en a pas.")
        Map<String, Object> details) {}
