package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(
    description =
        "Inscription ouverte d'un praticien (sans code d'invitation). L'adresse e-mail sert"
            + " d'identifiant de connexion.")
public record RegisterRequest(
    @Schema(
            description = "Adresse e-mail du compte (254 caractères au maximum).",
            example = "camille.martin@example.org")
        @NotBlank
        @Email
        @Size(max = 254)
        String email,
    @Schema(
            description =
                "Mot de passe (200 caractères au maximum). Politique : 12 caractères au moins,"
                    + " majuscules et minuscules, absent de la liste des mots de passe courants,"
                    + " sans e-mail ni nom (422 PASSWORD_POLICY sinon).")
        @NotBlank
        @Size(max = 200)
        String password,
    @Schema(description = "Prénom (100 caractères au maximum).", example = "Camille")
        @NotBlank
        @Size(max = 100)
        String firstName,
    @Schema(description = "Nom de famille (100 caractères au maximum).", example = "Martin")
        @NotBlank
        @Size(max = 100)
        String lastName,
    @Schema(
            description =
                "Profession exercée : psychologist (psychologue), psychiatrist (psychiatre) ou"
                    + " coach. Elle fixe les préférences par défaut du compte.",
            allowableValues = {"psychologist", "psychiatrist", "coach"},
            example = "psychologist")
        @NotBlank
        @Pattern(regexp = "psychologist|psychiatrist|coach")
        String profession,
    @Schema(
            description =
                "Numéro professionnel facultatif (ADELI, RPPS ou autre), 30 caractères au maximum"
                    + " ; absent ou vide : non renseigné.",
            example = "759912345")
        @Size(max = 30)
        String professionalNumber,
    @Schema(
            description =
                "Téléphone, 30 caractères au maximum ; normalisé au format E.164 (PHONE_INVALID"
                    + " si ce n'est pas un numéro plausible ; 06 12 34 56 78 est lu comme"
                    + " +33612345678).",
            example = "+33612345678")
        @NotBlank
        @Size(max = 30)
        String phone,
    @Schema(
            description =
                "Adresse du cabinet ; obligatoire sauf si remoteOnly vaut true (422"
                    + " ADDRESS_REQUIRED sinon).")
        @Valid
        Address address,
    @Schema(
            description =
                "true si le praticien exerce uniquement à distance (visio) : l'adresse n'est"
                    + " alors pas exigée. Absent = false.")
        Boolean remoteOnly,
    @Schema(description = "Consentements CGU et confidentialité, tous deux à true.") @NotNull @Valid
        Consents consents) {}
