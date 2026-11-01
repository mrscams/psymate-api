package com.psymate.identity.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Corps des réponses et requêtes de l'administration des dossiers. Jamais de clé de stockage,
 * d'empreinte, de hachage de mot de passe ni de donnée 2FA ; aucune donnée patient.
 */
final class AdminDtos {

  private AdminDtos() {}

  @Schema(
      description =
          "Ligne de la file de vérification : un dossier professionnel soumis, à traiter par un"
              + " administrateur.")
  record QueueItemDto(
      @Schema(
              description = "Identifiant du praticien dont le dossier est à vérifier.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID practitionerId,
      @Schema(
              description = "Nom d'affichage du praticien (prénom et nom).",
              example = "Claire Martin")
          String displayName,
      @Schema(
              description = "Profession déclarée.",
              allowableValues = {"psychologist", "psychiatrist", "coach"})
          String profession,
      @Schema(
              description = "Ville du cabinet ; null si aucune adresse n'a été renseignée.",
              example = "Lyon")
          String city,
      @Schema(
              description =
                  "Nombre de justificatifs de vérification non retirés par le praticien, y compris"
                      + " ceux déjà purgés.",
              example = "2")
          int documentsCount,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de la dernière soumission du dossier ; null si"
                      + " aucune soumission n'est journalisée.",
              example = "2026-03-14T09:30:00Z")
          Instant submittedAt,
      @Schema(
              description =
                  "Vrai si le dossier est en statut under_review depuis plus de 2 jours ouvrés"
                      + " après submittedAt ; toujours faux pour needs_more ou sans submittedAt.")
          boolean overdue,
      @Schema(
              description =
                  "Types de pièces obligatoires absents du dossier (parmi diploma et identity ; un"
                      + " justificatif purgé ne compte pas) ; liste vide si le dossier est"
                      + " complet.")
          List<String> missingHints) {}

  @Schema(
      description =
          "Page de la file de vérification. Tri par date de soumission (à défaut dernière mise à"
              + " jour du compte), selon le paramètre sort.")
  record QueueDto(
      @Schema(description = "Dossiers de la page, dans l'ordre du tri demandé.")
          List<QueueItemDto> items,
      @Schema(
              description =
                  "Curseur opaque à renvoyer dans le paramètre cursor pour la page suivante ; null"
                      + " sur la dernière page.")
          String nextCursor) {}

  @Schema(description = "Adresse professionnelle du praticien, telle que saisie dans son profil.")
  record AddressDto(
      @Schema(description = "Numéro et nom de la rue ; null si non renseigné.") String street,
      @Schema(description = "Code postal ; null si non renseigné.") String postalCode,
      @Schema(description = "Ville ; null si non renseignée.") String city) {}

  @Schema(description = "Coordonnées professionnelles du praticien, vues par l'administration.")
  record ContactDto(
      @Schema(
              description = "Téléphone professionnel ; null si non renseigné.",
              example = "+33 4 72 00 00 00")
          String phone,
      @Schema(
              description =
                  "Numéro professionnel (RPPS, ADELI ou équivalent) ; null si non renseigné.")
          String professionalNumber,
      @Schema(description = "Adresse du cabinet ; ses champs sont null si non renseignés.")
          AddressDto address,
      @Schema(description = "Vrai si le praticien exerce uniquement à distance.")
          boolean remoteOnly) {}

  @Schema(
      description =
          "Justificatif du dossier de vérification d'un praticien. Le contenu ne s'ouvre que par"
              + " une URL signée (getAdminDocumentUrl).")
  record DocumentDto(
      @Schema(
              description = "Identifiant du justificatif.",
              example = "4d8f1e52-7a3b-4c19-8e60-1f2a3b4c5d6e")
          UUID id,
      @Schema(
              description = "Nature de la pièce.",
              allowableValues = {
                "diploma",
                "registration",
                "certification",
                "professional_card",
                "identity",
                "other"
              })
          String type,
      @Schema(description = "Intitulé donné par le praticien.", example = "Master de psychologie")
          String title,
      @Schema(
              description = "Organisme émetteur ; null si non renseigné.",
              example = "Université Lyon 2")
          String issuer,
      @Schema(
              description = "Année d'obtention (1950 à 2100) ; null si non renseignée.",
              example = "2014")
          Integer year,
      @Schema(
              description = "Type MIME du fichier.",
              allowableValues = {"application/pdf", "image/jpeg", "image/png"})
          String mime,
      @Schema(description = "Taille du fichier en octets (10 Mo au plus).", example = "482113")
          int sizeBytes,
      @Schema(description = "Date-heure UTC (ISO 8601) du dépôt.", example = "2026-03-14T09:12:00Z")
          Instant uploadedAt,
      @Schema(
              description =
                  "Qualification par l'administration : pending (pas encore examiné), conform ou"
                      + " non_conform.",
              allowableValues = {"pending", "conform", "non_conform"})
          String reviewedStatus,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de la dernière qualification ; null tant que le"
                      + " justificatif n'a pas été qualifié.",
              example = "2026-03-14T10:05:00Z")
          Instant reviewedAt,
      @Schema(
              description =
                  "Vrai si le fichier a été purgé (90 jours après la décision) : il n'est plus"
                      + " téléchargeable (410 ADMIN_DOCUMENT_PURGED), seules les métadonnées"
                      + " restent.")
          boolean purged) {}

  @Schema(
      description =
          "Décision ou acte d'administration enregistré sur un compte (historique append-only,"
              + " renvoyé du plus récent au plus ancien).")
  record DecisionItemDto(
      @Schema(
              description = "Identifiant de la décision.",
              example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
          UUID id,
      @Schema(
              description =
                  "Nature de l'acte : approved, needs_more, rejected (vérification du dossier),"
                      + " suspended, reactivated (gestion du compte), appeal_accepted,"
                      + " appeal_rejected (recours).",
              allowableValues = {
                "approved",
                "needs_more",
                "rejected",
                "suspended",
                "reactivated",
                "appeal_accepted",
                "appeal_rejected"
              })
          String kind,
      @Schema(
              description =
                  "Message transmis au praticien (complément demandé, motif du refus ou de la"
                      + " suspension, réponse au recours) ; null s'il n'y en a pas (par exemple une"
                      + " approbation sans message).")
          String messageToPractitioner,
      @Schema(
              description =
                  "Note interne de l'administration, jamais transmise au praticien ; null si"
                      + " absente.")
          String internalNote,
      @Schema(
              description =
                  "Liste de contrôle libre saisie avec la décision (clés et valeurs JSON). Pour les"
                      + " actes de gestion de compte, contient les statuts avant et après"
                      + " (previousStatus, newStatus) ou la décision contestée (decisionId) ;"
                      + " objet vide s'il n'y en a pas.")
          Map<String, Object> checklist,
      @Schema(
              description = "Date-heure UTC (ISO 8601) de la décision.",
              example = "2026-03-15T08:00:00Z")
          Instant decidedAt,
      @Schema(
              description = "Identifiant de l'administrateur auteur de la décision.",
              example = "5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f")
          UUID adminId) {}

  @Schema(
      description =
          "Dossier complet d'un praticien pour la vérification : identité, coordonnées,"
              + " justificatifs et historique des décisions. Aucune donnée patient.")
  record DossierDto(
      @Schema(
              description = "Identifiant du praticien.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID practitionerId,
      @Schema(description = "Nom d'affichage.", example = "Claire Martin") String displayName,
      @Schema(description = "Prénom.", example = "Claire") String firstName,
      @Schema(description = "Nom de famille.", example = "Martin") String lastName,
      @Schema(description = "Adresse e-mail de connexion.", example = "claire.martin@example.org")
          String email,
      @Schema(
              description = "Profession déclarée.",
              allowableValues = {"psychologist", "psychiatrist", "coach"})
          String profession,
      @Schema(description = "Langue de l'interface du praticien (code ISO 639-1).", example = "fr")
          String locale,
      @Schema(
              description =
                  "État de vérification du compte : email_pending, docs_pending, under_review,"
                      + " approved, needs_more, rejected, suspended ou closed.",
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
          String verificationStatus,
      @Schema(
              description = "Date-heure UTC (ISO 8601) de création du compte.",
              example = "2026-03-10T14:00:00Z")
          Instant createdAt,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de confirmation de l'e-mail ; null si non confirmé.",
              example = "2026-03-10T14:05:00Z")
          Instant emailConfirmedAt,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de la dernière soumission du dossier ; null si"
                      + " aucune soumission n'est journalisée.",
              example = "2026-03-14T09:30:00Z")
          Instant submittedAt,
      @Schema(
              description =
                  "Vrai si le dossier est under_review depuis plus de 2 jours ouvrés après"
                      + " submittedAt.")
          boolean overdue,
      @Schema(
              description =
                  "Date-heure UTC (ISO 8601) de la dernière approbation ; null si le dossier n'a"
                      + " jamais été approuvé.",
              example = "2026-03-15T08:00:00Z")
          Instant verifiedAt,
      @Schema(description = "Coordonnées professionnelles.") ContactDto contact,
      @Schema(
              description =
                  "Justificatifs de vérification non retirés par le praticien, du plus ancien au"
                      + " plus récent.")
          List<DocumentDto> documents,
      @Schema(
              description =
                  "Historique des décisions sur ce compte, du plus récent au plus ancien.")
          List<DecisionItemDto> decisions) {}

  @Schema(description = "URL signée et éphémère pour ouvrir un justificatif.")
  record SignedUrlDto(
      @Schema(
              description =
                  "Chemin relatif de l'API (getAdminDocumentFile) avec exp et sig en paramètres de"
                      + " requête ; valable uniquement pour l'administrateur qui l'a demandée.",
              example =
                  "/api/admin/verifications/9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e/documents/4d8f1e52-7a3b-4c19-8e60-1f2a3b4c5d6e/file?exp=1773564900&sig=Zm9v")
          String url,
      @Schema(
              description = "Date-heure UTC (ISO 8601) d'expiration, 5 minutes après l'émission.",
              example = "2026-03-15T08:05:00Z")
          Instant expiresAt) {}

  @Schema(description = "Qualification d'un justificatif par l'administration.")
  record ReviewRequest(
      @Schema(
              description =
                  "Nouvel état du justificatif : pending (à examiner), conform ou non_conform ;"
                      + " toute autre valeur donne 422 INVALID_REVIEWED_STATUS.",
              allowableValues = {"pending", "conform", "non_conform"})
          String reviewedStatus) {}

  /** Champs absents : checklist = {}, force = false. */
  @Schema(description = "Décision de l'administrateur sur un dossier en cours d'examen.")
  record DecisionRequest(
      @Schema(
              description =
                  "Issue : approved (dossier validé), needs_more (complément demandé) ou rejected"
                      + " (dossier refusé).",
              allowableValues = {"approved", "needs_more", "rejected"})
          String kind,
      @Schema(
              description =
                  "Message envoyé au praticien par e-mail ; obligatoire pour needs_more et"
                      + " rejected (422 MESSAGE_REQUIRED), 4000 caractères au plus ; facultatif"
                      + " pour approved.")
          String messageToPractitioner,
      @Schema(
              description =
                  "Note interne, jamais transmise au praticien, 4000 caractères au plus ;"
                      + " obligatoire pour approved avec force=true (422 NOTE_REQUIRED).")
          String internalNote,
      @Schema(
              description =
                  "Liste de contrôle libre (clés et valeurs JSON), 20 000 caractères au plus une"
                      + " fois sérialisée (422 TOO_LARGE) ; absent = objet vide.")
          Map<String, Object> checklist,
      @Schema(
              description =
                  "Pour approved uniquement : vrai pour approuver même si tous les justificatifs"
                      + " ne sont pas conformes ; une note interne est alors obligatoire. Absent ="
                      + " faux.")
          Boolean force) {}

  @Schema(description = "Décision enregistrée sur un dossier, avec le nouvel état de vérification.")
  record DecisionDto(
      @Schema(
              description = "Identifiant de la décision créée.",
              example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
          UUID id,
      @Schema(
              description = "Issue enregistrée.",
              allowableValues = {"approved", "needs_more", "rejected"})
          String kind,
      @Schema(
              description =
                  "Message au praticien enregistré (blancs de bord retirés) ; null si absent.")
          String messageToPractitioner,
      @Schema(description = "Note interne enregistrée (blancs de bord retirés) ; null si absente.")
          String internalNote,
      @Schema(description = "Liste de contrôle enregistrée ; objet vide s'il n'y en avait pas.")
          Map<String, Object> checklist,
      @Schema(
              description = "Date-heure UTC (ISO 8601) de la décision.",
              example = "2026-03-15T08:00:00Z")
          Instant decidedAt,
      @Schema(
              description = "Identifiant de l'administrateur qui a décidé.",
              example = "5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f")
          UUID adminId,
      @Schema(
              description =
                  "État de vérification du compte après la décision : approved, needs_more ou"
                      + " rejected.",
              allowableValues = {"approved", "needs_more", "rejected"})
          String verificationStatus) {}
}
