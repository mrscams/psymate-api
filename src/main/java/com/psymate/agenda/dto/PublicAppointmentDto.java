package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Rendez-vous pris en ligne, tel que le demandeur le voit par ses liens (K5.1, G Q80). Ne porte ni
 * le nom, ni l'e-mail, ni le téléphone du demandeur, ni aucune note.
 */
@Schema(
    description =
        "Rendez-vous pris en ligne, vu par le demandeur (lien de vérification ou de gestion). Ne"
            + " porte ni le nom, ni l'e-mail, ni le téléphone du demandeur, ni aucune note.")
public record PublicAppointmentDto(
    @Schema(
            description =
                "État : pending (en attente de la réponse du praticien), confirmed (confirmé, à"
                    + " venir ou en cours), cancelled (annulé), past (passé : confirmé dont la fin"
                    + " est passée, ou séance tenue).",
            allowableValues = {"pending", "confirmed", "cancelled", "past"},
            example = "pending")
        String status,
    @Schema(
            description =
                "Qui a annulé, quand status = cancelled : visitor (le demandeur), practitioner"
                    + " (refus ou annulation par le praticien), expired (demande sans réponse) ;"
                    + " null sinon.",
            allowableValues = {"visitor", "practitioner", "expired"},
            example = "visitor")
        String cancelledBy,
    @Schema(
            description = "Début du rendez-vous, instant UTC.",
            format = "date-time",
            example = "2026-12-07T08:00:00Z")
        Instant startsAt,
    @Schema(
            description = "Fin du rendez-vous, instant UTC.",
            format = "date-time",
            example = "2026-12-07T09:00:00Z")
        Instant endsAt,
    @Schema(description = "Durée en minutes.", example = "60") int durationMin,
    @Schema(
            description = "Modalité du rendez-vous.",
            allowableValues = {"in_person", "video", "phone"},
            example = "in_person")
        String modality,
    PublicAppointmentPractitionerDto practitioner,
    @Schema(
            description =
                "Vrai si le demandeur peut encore annuler : rendez-vous pending ou confirmed qui"
                    + " n'a pas commencé.",
            example = "true")
        boolean canCancel,
    @Schema(
            description =
                "Jeton du lien de gestion (/rendez-vous/gerer/{manageToken}). Renvoyé une seule"
                    + " fois, par la première vérification réussie ; null dans toute autre"
                    + " réponse. Seule son empreinte est gardée : il ne peut pas être renvoyé.",
            example = "Zk3vN8xQ1wE5rT7yU9iO2pA4sD6fG8hJ0kL3zX5cV7b")
        String manageToken) {

  /** Sans le jeton de gestion en clair : il vaut accès au rendez-vous. */
  @Override
  public String toString() {
    return "PublicAppointmentDto[status="
        + status
        + ", cancelledBy="
        + cancelledBy
        + ", startsAt="
        + startsAt
        + ", endsAt="
        + endsAt
        + ", durationMin="
        + durationMin
        + ", modality="
        + modality
        + ", practitioner="
        + practitioner
        + ", canCancel="
        + canCancel
        + ", manageToken="
        + CreateBookingRequest.mask(manageToken)
        + "]";
  }
}
