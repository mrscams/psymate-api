package com.psymate.directory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Corps du formulaire de contact public (fiche 09, F26). */
@Schema(description = "Message d'un visiteur à un praticien, relayé par e-mail.")
record SubmitContactRequest(
    @Schema(description = "Nom du visiteur", maxLength = 200) @NotBlank @Size(max = 200)
        String name,
    @Schema(description = "E-mail du visiteur (réponse du praticien)", format = "email")
        @NotBlank
        @Email
        @Size(max = 320)
        String email,
    @Schema(description = "Objet (optionnel)", maxLength = 200) @Size(max = 200) String subject,
    @Schema(description = "Message", maxLength = 4000) @NotBlank @Size(max = 4000) String message,
    @Schema(description = "Consentement au traitement du message ; doit valoir true")
        @NotNull
        @AssertTrue
        Boolean consent,
    @Schema(description = "Piège anti-spam (honeypot) : champ caché, doit rester vide")
        String website,
    @Schema(
            description =
                "Instant (ISO 8601, UTC) d'affichage du formulaire ; un envoi dans les 3 s"
                    + " est ignoré",
            format = "date-time")
        @NotNull
        Instant formShownAt) {}
