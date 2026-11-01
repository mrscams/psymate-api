package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminDtos.DecisionItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Corps des réponses et requêtes de l'administration des comptes (M1-14b) : liste, détail,
 * suspension, réactivation, journal et recours. Jamais de hachage, de donnée 2FA, d'adresse IP ni
 * de donnée patient.
 */
final class AdminAccountDtos {

  private AdminAccountDtos() {}

  /** État de vérification : statut, depuis quand, et retard de traitement (under_review). */
  @Schema(
      name = "AccountVerificationDto",
      description =
          "État de vérification d'un compte : statut, date d'entrée dans ce statut et retard de"
              + " traitement.")
  record VerificationDto(
      @Schema(
              description =
                  "Statut : email_pending (e-mail à confirmer), docs_pending (justificatifs à"
                      + " déposer), under_review (dossier soumis, en attente d'un administrateur),"
                      + " approved, needs_more (complément demandé), rejected (dossier refusé),"
                      + " suspended ou closed.",
              allowableValues = {
                "email_pending",
                "docs_pending",
                "under_review",
                "approved",
                "needs_more",
                "rejected",
                "suspended",
                "closed"
              })
          String status,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) d'entrée dans le statut courant : approbation pour"
                      + " approved, soumission pour under_review, suspension ou refus pour"
                      + " suspended, rejected et closed ; à défaut, dernière mise à jour du"
                      + " compte.",
              example = "2026-03-15T08:00:00Z")
          Instant since,
      @Schema(
              description =
                  "Vrai si le dossier est under_review depuis plus de 2 jours ouvrés après sa"
                      + " soumission ; faux pour tout autre statut.")
          boolean overdue) {}

  @Schema(
      description = "Compte d'un praticien ou d'un administrateur dans la liste d'administration.")
  record AccountItemDto(
      @Schema(
              description = "Identifiant du compte.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID id,
      @Schema(description = "Nom d'affichage.", example = "Claire Martin") String displayName,
      @Schema(description = "Adresse e-mail de connexion.", example = "claire.martin@example.org")
          String email,
      @Schema(
              description = "Profession déclarée.",
              allowableValues = {"psychologist", "psychiatrist", "coach"})
          String profession,
      @Schema(
              description = "Ville du cabinet ; null si aucune adresse n'a été renseignée.",
              example = "Lyon")
          String city,
      @Schema(
              description = "Rôle du compte : practitioner ou admin.",
              allowableValues = {"practitioner", "admin"})
          String role,
      @Schema(description = "État de vérification du compte.") VerificationDto verification,
      @Schema(description = "Vrai si la double authentification est activée sur le compte.")
          boolean twoFactorEnabled,
      @Schema(
              description = "Date-heure UTC (ISO 8601) de création du compte.",
              example = "2026-03-10T14:00:00Z")
          Instant createdAt,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de la dernière connexion ; null si le compte ne s'est"
                      + " jamais connecté.",
              example = "2026-03-20T07:45:00Z")
          Instant lastLoginAt,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de la suspension ou du refus ; null si le compte n'est"
                      + " ni suspendu ni refusé (effacée à la réactivation).",
              example = "2026-04-02T10:00:00Z")
          Instant suspendedAt,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de la purge planifiée du compte : 30 jours après le"
                      + " refus du dossier ; null sinon (effacée à la réactivation).",
              example = "2026-05-02T10:00:00Z")
          Instant purgeAt) {}

  /**
   * Comptes d'une page. counts : approved, under_review, needs_more, suspended, rejected, sur
   * l'ensemble des comptes qui respectent q, role et profession (le filtre status n'y entre pas,
   * pour que les onglets restent justes).
   */
  @Schema(
      description =
          "Page de comptes, triés du plus récemment créé au plus ancien, avec le nombre de comptes"
              + " par statut.")
  record AccountListDto(
      @Schema(description = "Comptes de la page.") List<AccountItemDto> items,
      @Schema(
              description =
                  "Curseur opaque à renvoyer dans le paramètre cursor pour la page suivante ; null"
                      + " sur la dernière page.")
          String nextCursor,
      @Schema(
              description =
                  "Nombre de comptes par statut, avec les clés approved, under_review, needs_more,"
                      + " suspended et rejected (0 si aucun). Calculé sur tous les comptes qui"
                      + " respectent q, role et profession, sans tenir compte du filtre status ni"
                      + " de la pagination, pour que les onglets restent justes.")
          Map<String, Integer> counts) {}

  @Schema(
      description = "Résumé d'un recours déposé par le praticien, dans le détail de son compte.")
  record AppealSummaryDto(
      @Schema(
              description = "Identifiant du recours.",
              example = "7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b")
          UUID id,
      @Schema(
              description = "Identifiant de la décision (refus ou suspension) contestée.",
              example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
          UUID decisionId,
      @Schema(
              description = "Date-heure UTC (ISO 8601) du dépôt du recours.",
              example = "2026-04-03T09:00:00Z")
          Instant submittedAt,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de l'examen ; null tant que le recours est ouvert.",
              example = "2026-04-05T14:00:00Z")
          Instant reviewedAt,
      @Schema(
              description =
                  "Issue : accepted (compte réactivé) ou dismissed (recours écarté) ; null tant que"
                      + " le recours est ouvert.",
              allowableValues = {"accepted", "dismissed"})
          String outcome,
      @Schema(description = "Message du praticien (20 à 4000 caractères).") String message,
      @Schema(
              description =
                  "Réponse de l'administrateur au praticien ; null tant que le recours est"
                      + " ouvert.")
          String responseMessage) {}

  @Schema(
      description =
          "Détail d'un compte : mêmes champs que dans la liste, plus l'historique des décisions et"
              + " des recours.")
  record AccountDetailDto(
      @Schema(
              description = "Identifiant du compte.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID id,
      @Schema(description = "Nom d'affichage.", example = "Claire Martin") String displayName,
      @Schema(description = "Adresse e-mail de connexion.", example = "claire.martin@example.org")
          String email,
      @Schema(
              description = "Profession déclarée.",
              allowableValues = {"psychologist", "psychiatrist", "coach"})
          String profession,
      @Schema(
              description = "Ville du cabinet ; null si aucune adresse n'a été renseignée.",
              example = "Lyon")
          String city,
      @Schema(
              description = "Rôle du compte : practitioner ou admin.",
              allowableValues = {"practitioner", "admin"})
          String role,
      @Schema(description = "État de vérification du compte.") VerificationDto verification,
      @Schema(description = "Vrai si la double authentification est activée sur le compte.")
          boolean twoFactorEnabled,
      @Schema(
              description = "Date-heure UTC (ISO 8601) de création du compte.",
              example = "2026-03-10T14:00:00Z")
          Instant createdAt,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de la dernière connexion ; null si le compte ne s'est"
                      + " jamais connecté.",
              example = "2026-03-20T07:45:00Z")
          Instant lastLoginAt,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de la suspension ou du refus ; null si le compte n'est"
                      + " ni suspendu ni refusé (effacée à la réactivation).",
              example = "2026-04-02T10:00:00Z")
          Instant suspendedAt,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de la purge planifiée du compte : 30 jours après le"
                      + " refus du dossier ; null sinon (effacée à la réactivation).",
              example = "2026-05-02T10:00:00Z")
          Instant purgeAt,
      @Schema(
              description =
                  "Décisions prises sur ce compte (vérification, suspension, réactivation,"
                      + " recours), de la plus récente à la plus ancienne.")
          List<DecisionItemDto> decisions,
      @Schema(description = "Recours déposés par le praticien, du plus récent au plus ancien.")
          List<AppealSummaryDto> appeals) {}

  @Schema(description = "Motif d'une suspension ou d'une réactivation manuelle.")
  record ReasonRequest(
      @Schema(
              description =
                  "Motif de l'action, de 20 à 4000 caractères après suppression des blancs de"
                      + " bord ; enregistré au journal (repris dans l'e-mail au praticien pour une"
                      + " suspension). Erreurs 422 :"
                      + " REQUIRED, TOO_SHORT, TOO_LONG.",
              minLength = 20,
              maxLength = 4000,
              requiredMode = Schema.RequiredMode.REQUIRED)
          String reason) {}

  @Schema(description = "Résultat d'une suspension de compte.")
  record SuspendDto(
      @Schema(
              description = "Identifiant du compte suspendu.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID id,
      @Schema(
              description = "Nouveau statut de vérification, toujours suspended.",
              example = "suspended")
          String verificationStatus,
      @Schema(
              description = "Date-heure UTC (ISO 8601) de la suspension.",
              example = "2026-04-02T10:00:00Z")
          Instant suspendedAt,
      @Schema(
              description = "Identifiant de la décision de suspension enregistrée.",
              example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
          UUID decisionId) {}

  @Schema(description = "Résultat d'une réactivation de compte.")
  record ReactivateDto(
      @Schema(
              description = "Identifiant du compte réactivé.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID id,
      @Schema(
              description =
                  "Statut de vérification rendu au compte : docs_pending pour un dossier refusé"
                      + " (le dossier doit être redéposé), sinon le statut d'avant la suspension"
                      + " (docs_pending, under_review, approved ou needs_more).",
              allowableValues = {"docs_pending", "under_review", "approved", "needs_more"})
          String verificationStatus,
      @Schema(
              description = "Identifiant de la décision de réactivation enregistrée.",
              example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
          UUID decisionId) {}

  // ---- journal

  @Schema(description = "Administrateur auteur d'un événement du journal.")
  record ActorDto(
      @Schema(
              description = "Identifiant de l'administrateur.",
              example = "5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f")
          UUID id,
      @Schema(description = "Nom d'affichage de l'administrateur.", example = "Julien Bernard")
          String displayName) {}

  @Schema(
      description =
          "Ligne du journal d'administration : une décision ou un événement de sécurité. Jamais"
              + " d'adresse IP ni de donnée patient.")
  record JournalItemDto(
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

  @Schema(description = "Page du journal d'administration, des plus récents aux plus anciens.")
  record JournalDto(
      @Schema(description = "Lignes du journal de la page.") List<JournalItemDto> items,
      @Schema(
              description =
                  "Curseur opaque à renvoyer dans le paramètre cursor pour la page suivante ; null"
                      + " sur la dernière page.")
          String nextCursor) {}

  // ---- recours

  @Schema(
      description =
          "Dépôt d'un recours par un praticien dont le compte est refusé ou suspendu. Public : le"
              + " praticien s'identifie par son e-mail et son mot de passe dans le corps, car il"
              + " n'a pas de session.")
  record AppealRequest(
      @Schema(
              description = "Adresse e-mail du compte (insensible à la casse).",
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "claire.martin@example.org")
          String email,
      @Schema(
              description = "Mot de passe du compte.",
              requiredMode = Schema.RequiredMode.REQUIRED,
              accessMode = Schema.AccessMode.WRITE_ONLY)
          String password,
      @Schema(
              description =
                  "Code à usage unique de la double authentification (ou code de secours) ;"
                      + " obligatoire si la 2FA est active, sinon la réponse est 202 avec"
                      + " otpRequired. Facultatif sinon.",
              example = "123456",
              accessMode = Schema.AccessMode.WRITE_ONLY)
          String otp,
      @Schema(
              description =
                  "Argumentaire du recours, de 20 à 4000 caractères après suppression des blancs"
                      + " de bord. Erreurs 422 : REQUIRED, TOO_SHORT, TOO_LONG.",
              minLength = 20,
              maxLength = 4000,
              requiredMode = Schema.RequiredMode.REQUIRED)
          String message) {}

  @Schema(description = "Recours enregistré.")
  record AppealCreatedDto(
      @Schema(
              description = "Identifiant du recours créé.",
              example = "7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b")
          UUID id,
      @Schema(description = "Date-heure UTC (ISO 8601) du dépôt.", example = "2026-04-03T09:00:00Z")
          Instant submittedAt,
      @Schema(description = "Statut du recours, toujours open à la création.", example = "open")
          String status) {}

  @Schema(
      name = "AppealOtpRequiredDto",
      description =
          "Corps du 202 : le mot de passe est correct, la double authentification est active et le"
              + " code est absent ; rien n'a été enregistré, renvoyer la demande avec otp.")
  record OtpRequiredDto(
      @Schema(description = "Toujours vrai.", example = "true") boolean otpRequired) {}

  @Schema(
      description =
          "Recours vu par l'administration, avec la décision contestée et l'issue éventuelle.")
  record AppealItemDto(
      @Schema(
              description = "Identifiant du recours.",
              example = "7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b")
          UUID id,
      @Schema(
              description = "Identifiant du praticien qui conteste.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID practitionerId,
      @Schema(description = "Nom d'affichage du praticien.", example = "Claire Martin")
          String practitionerDisplayName,
      @Schema(
              description =
                  "Identifiant de la décision contestée (dernier refus ou dernière suspension).",
              example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
          UUID decisionId,
      @Schema(
              description =
                  "Nature de la décision contestée : rejected (dossier refusé) ou suspended.",
              allowableValues = {"rejected", "suspended"})
          String decisionKind,
      @Schema(
              description =
                  "Administrateur auteur de la décision contestée ; il ne peut pas examiner le"
                      + " recours (409 APPEAL_SELF_REVIEW).",
              example = "5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f")
          UUID decisionAdminId,
      @Schema(description = "Message du praticien (20 à 4000 caractères).") String message,
      @Schema(description = "Date-heure UTC (ISO 8601) du dépôt.", example = "2026-04-03T09:00:00Z")
          Instant submittedAt,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de l'examen ; null tant que le recours est ouvert.",
              example = "2026-04-05T14:00:00Z")
          Instant reviewedAt,
      @Schema(
              description =
                  "Administrateur qui a examiné le recours ; null tant que le recours est ouvert.")
          UUID reviewedBy,
      @Schema(
              description =
                  "Issue : accepted (compte réactivé) ou dismissed (recours écarté) ; null tant que"
                      + " le recours est ouvert.",
              allowableValues = {"accepted", "dismissed"})
          String outcome,
      @Schema(
              description =
                  "Réponse de l'administrateur au praticien ; null tant que le recours est"
                      + " ouvert.")
          String responseMessage) {}

  @Schema(description = "Page de recours.")
  record AppealListDto(
      @Schema(
              description =
                  "Recours de la page : du plus ancien au plus récent pour status=open, du plus"
                      + " récent au plus ancien pour decided et all.")
          List<AppealItemDto> items,
      @Schema(
              description =
                  "Curseur opaque à renvoyer dans le paramètre cursor pour la page suivante ; null"
                      + " sur la dernière page.")
          String nextCursor) {}

  @Schema(description = "Décision d'un administrateur sur un recours ouvert.")
  record AppealDecisionRequest(
      @Schema(
              description =
                  "Issue : accepted (le compte est réactivé) ou dismissed (le compte reste"
                      + " inchangé). Autre valeur : 422 INVALID_OUTCOME.",
              allowableValues = {"accepted", "dismissed"},
              requiredMode = Schema.RequiredMode.REQUIRED)
          String outcome,
      @Schema(
              description =
                  "Réponse transmise au praticien par e-mail, obligatoire (422 REQUIRED), 4000"
                      + " caractères au plus (422 TOO_LONG).",
              maxLength = 4000,
              requiredMode = Schema.RequiredMode.REQUIRED)
          String messageToPractitioner) {}
}
