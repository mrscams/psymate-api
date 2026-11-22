package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Demande de rendez-vous en ligne d'un visiteur ({@code POST
 * /api/public/booking/practitioners/{slug}/requests}, G Q72) : identité, créneau, modalité,
 * consentement, et les deux champs anti-spam du formulaire de contact. Aucun champ libre. Le
 * message {@code INVALID_FORMAT} de l'e-mail et du téléphone est le code de champ renvoyé (lu par
 * {@code AgendaExceptionHandler}).
 */
@Schema(
    description =
        "Demande de rendez-vous en ligne (G Q72). Aucun champ libre : ni motif, ni message. La"
            + " demande n'est rattachée à aucun dossier ; rien n'entre dans l'agenda avant la"
            + " vérification de l'e-mail.")
public record CreateBookingRequest(
    @Schema(
            description =
                "Début du créneau choisi, instant UTC ISO 8601 : exactement le startsAt d'un"
                    + " créneau offert (GET …/slots). Obligatoire.",
            format = "date-time",
            example = "2026-12-07T08:00:00Z")
        @NotNull
        Instant startsAt,
    @Schema(
            description =
                "Modalité, parmi celles que le praticien offre (409 SLOT_NOT_AVAILABLE sinon)."
                    + " Obligatoire.",
            allowableValues = {"in_person", "video", "phone"},
            example = "in_person")
        @NotNull
        @Pattern(regexp = "in_person|video|phone")
        String modality,
    @Schema(
            description = "Prénom, 100 caractères au plus. Obligatoire.",
            maxLength = 100,
            example = "Sara")
        @NotBlank
        @Size(max = 100)
        String firstName,
    @Schema(
            description = "Nom, 100 caractères au plus. Obligatoire.",
            maxLength = 100,
            example = "Benali")
        @NotBlank
        @Size(max = 100)
        String lastName,
    @Schema(
            description =
                "E-mail, 320 caractères au plus (OUT_OF_RANGE sinon) : une seule @, au moins un"
                    + " point après elle, aucun espace (INVALID_FORMAT sinon). Obligatoire : le"
                    + " lien de vérification y est envoyé.",
            format = "email",
            maxLength = 320,
            example = "sara.benali@example.test")
        @NotBlank
        @Size(max = 320)
        @Pattern(regexp = GuestRequest.EMAIL, message = GuestRequest.INVALID_FORMAT)
        String email,
    @Schema(
            description =
                "Téléphone, facultatif, 30 caractères au plus : chiffres, espaces, +, point,"
                    + " tiret, parenthèses, au moins un chiffre (INVALID_FORMAT sinon) ; chaîne"
                    + " vide : aucun téléphone.",
            maxLength = 30,
            example = "+33 6 12 34 56 78")
        @Size(max = 30)
        @Pattern(regexp = GuestRequest.PHONE, message = GuestRequest.INVALID_FORMAT)
        String phone,
    @Schema(
            description = "Langue des e-mails envoyés au demandeur. Défaut : fr.",
            allowableValues = {"fr", "en"},
            example = "fr")
        @Pattern(regexp = "fr|en")
        String locale,
    @Schema(
            description =
                "Consentement du demandeur au traitement de sa demande ; doit valoir true (422"
                    + " INVALID_VALUE sinon, REQUIRED s'il manque).",
            example = "true")
        @NotNull
        @AssertTrue
        Boolean consent,
    @Schema(
            description =
                "Piège anti-spam (honeypot) : champ caché du formulaire, à laisser vide ; rempli,"
                    + " la demande est ignorée en silence (202 sans ligne ni e-mail).")
        String website,
    @Schema(
            description =
                "Instant d'affichage du formulaire, ISO 8601 UTC. Obligatoire. Une demande"
                    + " envoyée moins de 3 secondes après cet instant, ou dans le futur, est"
                    + " ignorée en silence (202 sans ligne ni e-mail).",
            format = "date-time",
            example = "2026-12-01T14:30:00Z")
        @NotNull
        Instant formShownAt) {}
