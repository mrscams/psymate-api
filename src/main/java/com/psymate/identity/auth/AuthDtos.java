package com.psymate.identity.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** DTO de l'API d'authentification. */
final class AuthDtos {

  private AuthDtos() {}

  /** Adresse du cabinet ; les champs sont contrôlés par le service (obligatoires sauf visio). */
  @Schema(
      description =
          "Adresse du cabinet. Obligatoire (rue, code postal, ville) sauf si remoteOnly vaut true ;"
              + " contrôlée par le service, erreurs par champ en 422 VALIDATION_FAILED.")
  record Address(
      @Schema(
              description = "Numéro et nom de la rue (200 caractères au maximum).",
              example = "12 rue de la Paix")
          @Size(max = 200)
          String street,
      @Schema(
              description =
                  "Code postal français à 5 chiffres (POSTAL_CODE_INVALID sinon ; 20 caractères au"
                      + " maximum acceptés en entrée).",
              example = "75002")
          @Size(max = 20)
          String postalCode,
      @Schema(description = "Ville (100 caractères au maximum).", example = "Paris")
          @Size(max = 100)
          String city) {}

  /** Consentements obligatoires (CGU et politique de confidentialité). */
  @Schema(
      description =
          "Consentements obligatoires à l'inscription : les deux doivent valoir true (422"
              + " INVALID_FIELD sinon). Ils sont consignés dans le journal de sécurité.")
  record Consents(
      @Schema(description = "Acceptation des conditions générales d'utilisation ; true exigé.")
          @NotNull
          @AssertTrue
          Boolean terms,
      @Schema(description = "Acceptation de la politique de confidentialité ; true exigé.")
          @NotNull
          @AssertTrue
          Boolean privacy) {}

  @Schema(
      description =
          "Inscription ouverte d'un praticien (sans code d'invitation). L'adresse e-mail sert"
              + " d'identifiant de connexion.")
  record RegisterRequest(
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
      @Schema(description = "Consentements CGU et confidentialité, tous deux à true.")
          @NotNull
          @Valid
          Consents consents) {}

  /** Corps identique que le compte existe déjà ou non (anti-énumération). */
  @Schema(
      description =
          "Réponse de l'inscription, identique que l'adresse e-mail soit déjà inscrite ou non"
              + " (anti-énumération) : un e-mail de confirmation ou un e-mail « compte existant »"
              + " part dans les deux cas.")
  record RegisterResponse(
      @Schema(
              description = "Toujours confirmation_email_sent.",
              allowableValues = {"confirmation_email_sent"},
              example = "confirmation_email_sent")
          String status) {}

  @Schema(description = "Jeton de confirmation d'adresse e-mail reçu par e-mail.")
  record EmailConfirmRequest(
      @Schema(
              description =
                  "Jeton à usage unique reçu dans l'e-mail de confirmation, valable 24 h (200"
                      + " caractères au maximum).",
              example = "q3F9x0Zb7mYw2eKq5uVtJ1cH8aLrN4dS6pGoXi0TyUE")
          @NotBlank
          @Size(max = 200)
          String token) {}

  @Schema(description = "Demande de renvoi de l'e-mail de confirmation.")
  record EmailResendRequest(
      @Schema(
              description = "Adresse e-mail du compte non confirmé (254 caractères au maximum).",
              example = "camille.martin@example.org")
          @NotBlank
          @Email
          @Size(max = 254)
          String email) {}

  @Schema(
      description =
          "Correction de l'adresse e-mail d'un compte non confirmé ; le mot de passe prouve"
              + " l'identité de l'appelant (aucune session).")
  record EmailChangeRequest(
      @Schema(
              description = "Adresse e-mail actuelle du compte (254 caractères au maximum).",
              example = "camille.martin@example.org")
          @NotBlank
          @Size(max = 254)
          String email,
      @Schema(description = "Mot de passe actuel du compte (200 caractères au maximum).")
          @NotBlank
          @Size(max = 200)
          String password,
      @Schema(
              description =
                  "Nouvelle adresse e-mail qui recevra le lien de confirmation (254 caractères au"
                      + " maximum).",
              example = "camille.martin@example.org")
          @NotBlank
          @Email
          @Size(max = 254)
          String newEmail) {}

  @Schema(description = "État courant de la session du praticien connecté.")
  record SessionResponse(
      @Schema(description = "Identifiant du praticien.") UUID id,
      @Schema(description = "Adresse e-mail du compte.", example = "camille.martin@example.org")
          String email,
      @Schema(
              description = "Nom affiché (précédé de Dr pour un psychiatre).",
              example = "Camille Martin")
          String displayName,
      @Schema(
              description = "Profession : psychologist, psychiatrist ou coach.",
              allowableValues = {"psychologist", "psychiatrist", "coach"},
              example = "psychologist")
          String profession,
      @Schema(
              description =
                  "Statut de vérification à jour (relu en base à chaque appel) : email_pending ="
                      + " e-mail non confirmé ; docs_pending = justificatifs à déposer ;"
                      + " under_review = dossier en cours d'examen ; approved = praticien vérifié ;"
                      + " needs_more = pièces complémentaires demandées ; rejected = dossier refusé"
                      + " ; suspended = compte suspendu ; closed = compte clos.",
              allowableValues = {
                "email_pending",
                "docs_pending",
                "under_review",
                "approved",
                "needs_more",
                "rejected",
                "suspended",
                "closed"
              },
              example = "approved")
          String verificationStatus,
      @Schema(
              description =
                  "true si l'adresse e-mail est confirmée (verificationStatus différent de"
                      + " email_pending).")
          boolean emailConfirmed) {}

  /** otp : code TOTP à 6 chiffres ou code de secours ; rememberMe absent = false. */
  @Schema(description = "Identifiants de connexion ; otp seulement si la 2FA est active.")
  record LoginRequest(
      @Schema(
              description = "Adresse e-mail du compte (254 caractères au maximum).",
              example = "camille.martin@example.org")
          @NotBlank
          @Size(max = 254)
          String email,
      @Schema(description = "Mot de passe du compte (200 caractères au maximum).")
          @NotBlank
          @Size(max = 200)
          String password,
      @Schema(
              description =
                  "Code TOTP à 6 chiffres ou code de secours (32 caractères au maximum). À envoyer"
                      + " seulement si la 2FA est active ; s'il manque, la réponse est 202"
                      + " otpRequired.",
              example = "123456")
          @Size(max = 32)
          String otp,
      @Schema(
              description =
                  "true pour une session de 30 jours (cookie persistant) au lieu de 12 h"
                      + " d'inactivité. Absent = false.")
          Boolean rememberMe) {}

  /** 202 : mot de passe correct, la 2FA est active et le code manque. */
  @Schema(
      description =
          "Réponse 202 : le mot de passe est correct, la double authentification est active et le"
              + " code manque. Renvoyer la même requête avec le champ otp.")
  record OtpRequiredResponse(
      @Schema(description = "Toujours true.", example = "true") boolean otpRequired) {}

  @Schema(description = "Secret TOTP en attente d'activation, montré une seule fois.")
  record TwoFactorEnableResponse(
      @Schema(
              description =
                  "Secret TOTP en Base32, à saisir dans l'application d'authentification si le QR"
                      + " code n'est pas lisible.",
              example = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP")
          String secret,
      @Schema(
              description =
                  "URI otpauth:// à encoder en QR code (émetteur PsyMate, SHA1, 6 chiffres, pas de"
                      + " 30 s).",
              example =
                  "otpauth://totp/PsyMate:camille.martin%40example.org?secret=JBSWY3DPEHPK3PXP")
          String otpauthUri) {}

  @Schema(description = "Premier code TOTP produit par l'application d'authentification.")
  record TwoFactorVerifyRequest(
      @Schema(
              description = "Code TOTP à 6 chiffres affiché par l'application (32 caractères max).",
              example = "123456")
          @NotBlank
          @Size(max = 32)
          String code) {}

  @Schema(description = "Confirmation de la désactivation de la 2FA.")
  record TwoFactorDisableRequest(
      @Schema(description = "Mot de passe actuel du compte (200 caractères au maximum).")
          @NotBlank
          @Size(max = 200)
          String password,
      @Schema(
              description =
                  "Code TOTP à 6 chiffres ou code de secours non utilisé (32 caractères au"
                      + " maximum).",
              example = "123456")
          @NotBlank
          @Size(max = 32)
          String code) {}

  /** Codes de secours en clair : montrés une seule fois. */
  @Schema(description = "Codes de secours de la 2FA, en clair, montrés une seule fois.")
  record RecoveryCodesResponse(
      @Schema(
              description =
                  "Dix codes de secours à usage unique, au format XXXXX-XXXXX (majuscules et"
                      + " chiffres). Ils remplacent tous les codes précédents, qui ne"
                      + " fonctionnent plus.",
              example = "K7M2Q-9XWHD")
          java.util.List<String> recoveryCodes) {}

  @Schema(description = "Résultat de la révocation des autres sessions.")
  record RevokedSessionsResponse(
      @Schema(
              description =
                  "Nombre de sessions des autres appareils qui ont été révoquées (la session"
                      + " courante est conservée).",
              example = "2")
          int revoked) {}

  @Schema(description = "Demande d'envoi d'un lien de réinitialisation du mot de passe.")
  record PasswordResetRequest(
      @Schema(
              description = "Adresse e-mail du compte (254 caractères au maximum).",
              example = "camille.martin@example.org")
          @NotBlank
          @Email
          @Size(max = 254)
          String email) {}

  @Schema(description = "Nouveau mot de passe choisi à partir du lien de réinitialisation.")
  record PasswordResetConfirmRequest(
      @Schema(
              description =
                  "Jeton à usage unique reçu par e-mail, valable 30 minutes (200 caractères au"
                      + " maximum).",
              example = "q3F9x0Zb7mYw2eKq5uVtJ1cH8aLrN4dS6pGoXi0TyUE")
          @NotBlank
          @Size(max = 200)
          String token,
      @Schema(
              description =
                  "Nouveau mot de passe (200 caractères au maximum), soumis à la politique de mot"
                      + " de passe (422 PASSWORD_POLICY sinon ; le jeton n'est alors pas"
                      + " consommé).")
          @NotBlank
          @Size(max = 200)
          String password) {}

  /**
   * Changement de mot de passe public (compte de bootstrap) ; otp seulement si la 2FA est active.
   */
  @Schema(
      description =
          "Changement de mot de passe sans session : e-mail et mot de passe actuel prouvent"
              + " l'identité (compte de bootstrap à mot de passe à usage unique, ou changement"
              + " volontaire). otp seulement si la 2FA est active.")
  record PasswordChangeRequest(
      @Schema(
              description = "Adresse e-mail du compte (254 caractères au maximum).",
              example = "camille.martin@example.org")
          @NotBlank
          @Size(max = 254)
          String email,
      @Schema(description = "Mot de passe actuel (200 caractères au maximum).")
          @NotBlank
          @Size(max = 200)
          String currentPassword,
      @Schema(
              description =
                  "Nouveau mot de passe (200 caractères au maximum) : politique de mot de passe,"
                      + " différent de l'actuel (422 PASSWORD_POLICY ou PASSWORD_UNCHANGED).")
          @NotBlank
          @Size(max = 200)
          String newPassword,
      @Schema(
              description =
                  "Code TOTP à 6 chiffres ou code de secours (32 caractères au maximum), requis si"
                      + " la 2FA est active ; s'il manque, la réponse est 202 otpRequired.",
              example = "123456")
          @Size(max = 32)
          String otp) {}

  @Schema(description = "Praticien connecté, tel qu'ouvert par la session.")
  record MeResponse(
      @Schema(description = "Identifiant du praticien.") UUID id,
      @Schema(
              description =
                  "Identifiant du cabinet (tenant) du praticien ; toutes ses données y sont"
                      + " rattachées.")
          UUID tenantId,
      @Schema(description = "Adresse e-mail du compte.", example = "camille.martin@example.org")
          String email,
      @Schema(
              description = "Nom affiché (précédé de Dr pour un psychiatre).",
              example = "Camille Martin")
          String displayName,
      @Schema(
              description = "Profession : psychologist, psychiatrist ou coach.",
              allowableValues = {"psychologist", "psychiatrist", "coach"},
              example = "psychologist")
          String profession,
      @Schema(
              description =
                  "Statut de vérification du praticien lors de l'ouverture de la session :"
                      + " email_pending, docs_pending, under_review, approved, needs_more,"
                      + " rejected, suspended ou closed (GET /api/auth/session le relit à jour).",
              allowableValues = {
                "email_pending",
                "docs_pending",
                "under_review",
                "approved",
                "needs_more",
                "rejected",
                "suspended",
                "closed"
              },
              example = "approved")
          String verificationStatus) {}
}
