package com.psymate.directory;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Profil public tel que le voit son propriétaire. */
@Schema(
    description =
        "Profil d'annuaire du praticien connecté, tel qu'il le voit (tous les champs, y compris"
            + " ceux masqués au public par les interrupteurs show*).")
record ProfileDto(
    @Schema(
            description =
                "Adresse publique du profil, unique, dérivée du nom affiché à la création ; sert"
                    + " dans /api/public/directory/practitioners/{slug}.",
            example = "claire-lambert")
        String slug,
    @Schema(
            description =
                "true si le profil est visible dans l'annuaire public. Lecture seule : change"
                    + " uniquement par publish et unpublish.")
        boolean published,
    @Schema(description = "Nom affiché dans l'annuaire.", example = "Claire Lambert")
        String displayName,
    @Schema(
            description =
                "Profession du praticien, lue depuis son compte : psychologist, psychiatrist ou"
                    + " coach. Lecture seule.",
            example = "psychologist")
        String profession,
    @Schema(
            description = "Intitulé libre affiché sous le nom ; null si vide.",
            example = "Psychologue clinicienne")
        String titleLabel,
    @Schema(description = "true pour afficher publiquement le numéro professionnel du compte.")
        boolean showProfessionalNumber,
    @Schema(
            description =
                "Clé interne de stockage de la photo traitée ; null si aucune photo. Sert"
                    + " seulement à savoir si une photo existe.")
        String photoAssetKey,
    @Schema(description = "Présentation libre, 1 200 caractères au plus ; null si vide.")
        String bio,
    @Schema(
            description =
                "Codes de spécialités, parmi ceux de GET /api/vocabulary/directory (8 au plus)."
                    + " Liste vide si aucune.")
        List<String> specialties,
    @Schema(
            description =
                "Codes de publics accueillis, parmi ceux de GET /api/vocabulary/directory (adults,"
                    + " young_adults, adolescents, children, couples_families). Liste vide si"
                    + " aucun.")
        List<String> audiences,
    @Schema(
            description =
                "Modalités de consultation : in_person (au cabinet), video, home (à domicile)."
                    + " Liste vide si aucune.")
        List<String> modalities,
    @Schema(
            description =
                "Langues de consultation, codes ISO 639-1 (fr, en…). Liste vide si aucune.")
        List<String> languages,
    @Schema(description = "Ville ; null si vide.", example = "Lyon") String city,
    @Schema(description = "Code postal ; null si vide.", example = "69003") String postalCode,
    @Schema(
            description = "Code pays ISO 3166-1 alpha-2 en majuscules ; FR par défaut.",
            example = "FR")
        String country,
    @Schema(description = "Téléphone de contact ; null si vide.", example = "+33 4 72 00 00 00")
        String contactPhone,
    @Schema(
            description = "E-mail de contact public ; null si vide.",
            example = "contact@cabinet.example")
        String contactEmail,
    @Schema(
            description = "Site web, adresse http ou https ; null si vide.",
            example = "https://cabinet.example")
        String websiteUrl,
    @Schema(description = "Adresse postale du cabinet en texte libre ; null si vide.")
        String address,
    @Schema(description = "true pour afficher publiquement contactPhone.") boolean showPhone,
    @Schema(description = "true pour afficher publiquement contactEmail.") boolean showEmail,
    @Schema(description = "true pour afficher publiquement address.") boolean showAddress,
    @Schema(
            description = "Délai de réponse annoncé, texte libre ; null si vide.",
            example = "sous 48 h")
        String responseDelay,
    @Schema(description = "Tarifs, texte libre de 500 caractères au plus ; null si vide.")
        String fees,
    @Schema(
            description =
                "Disponibilité : accepting (accepte de nouveaux patients), waitlist (liste"
                    + " d'attente), closed (complet), unknown (non renseignée, valeur par défaut).",
            allowableValues = {"accepting", "waitlist", "closed", "unknown"},
            example = "accepting")
        String availability,
    @Schema(
            description = "Date du prochain créneau disponible, AAAA-MM-JJ ; null si inconnue.",
            example = "2026-11-20")
        LocalDate nextSlotAt,
    @Schema(
            description =
                "Date de la dernière publication, ISO 8601 UTC ; conservée après dépublication ;"
                    + " null si jamais publié.",
            example = "2026-10-12T14:30:00Z")
        OffsetDateTime publishedAt,
    @Schema(
            description = "Date de dernière modification du profil, ISO 8601 UTC.",
            example = "2026-10-12T14:30:00Z")
        OffsetDateTime updatedAt) {}
