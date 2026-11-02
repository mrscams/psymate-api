package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Corps du formulaire de contact public (fiche 09, F26). */
@Schema(
    description =
        "Message d'un visiteur à un praticien, relayé par e-mail. Il n'est rattaché à aucun"
            + " dossier patient.")
public record SubmitContactRequest(
    @Schema(
            description = "Nom du visiteur, 200 caractères au plus.",
            maxLength = 200,
            example = "Jean Dupont")
        @NotBlank
        @Size(max = 200)
        String name,
    @Schema(
            description =
                "E-mail du visiteur, 320 caractères au plus ; le praticien y répond directement.",
            format = "email",
            maxLength = 320,
            example = "jean.dupont@example.test")
        @NotBlank
        @Email
        @Size(max = 320)
        String email,
    @Schema(
            description = "Objet du message, 200 caractères au plus. Optionnel.",
            maxLength = 200,
            example = "Première consultation")
        @Size(max = 200)
        String subject,
    @Schema(
            description = "Texte du message, 4 000 caractères au plus.",
            maxLength = 4000,
            example = "Bonjour, avez-vous une place pour un premier rendez-vous ?")
        @NotBlank
        @Size(max = 4000)
        String message,
    @Schema(
            description =
                "Consentement du visiteur au traitement de son message ; doit valoir true (422"
                    + " sinon).",
            example = "true")
        @NotNull
        @AssertTrue
        Boolean consent,
    @Schema(
            description =
                "Piège anti-spam (honeypot) : champ caché du formulaire, à laisser vide ; rempli,"
                    + " le message est ignoré en silence (202 sans envoi).")
        String website,
    @Schema(
            description =
                "Instant d'affichage du formulaire, ISO 8601 UTC ; un envoi moins de 3 secondes"
                    + " après cet instant, ou dans le futur, est ignoré en silence (202 sans"
                    + " envoi).",
            format = "date-time",
            example = "2026-10-12T14:30:00Z")
        @NotNull
        Instant formShownAt) {}
