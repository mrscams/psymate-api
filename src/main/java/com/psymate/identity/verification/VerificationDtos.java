package com.psymate.identity.verification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Corps des réponses et requêtes du dossier professionnel. */
final class VerificationDtos {

  private VerificationDtos() {}

  /** Justificatif : jamais de clé de stockage ni d'empreinte. */
  @Schema(
      description =
          "Justificatif déposé dans le dossier professionnel. Aucune clé de stockage ni empreinte"
              + " n'est exposée ; le fichier se lit via GET"
              + " /api/me/verification/documents/{id}/url.")
  record DocumentDto(
      @Schema(description = "Identifiant du justificatif.") UUID id,
      @Schema(
              description =
                  "Nature du justificatif : diploma = diplôme, registration = attestation"
                      + " d'inscription (ADELI ou RPPS), certification = certification (coach),"
                      + " professional_card = carte professionnelle, identity = pièce d'identité,"
                      + " other = autre pièce. Un diplôme et une pièce d'identité sont obligatoires"
                      + " pour soumettre le dossier.",
              allowableValues = {
                "diploma",
                "registration",
                "certification",
                "professional_card",
                "identity",
                "other"
              },
              example = "diploma")
          String type,
      @Schema(
              description = "Intitulé saisi par le praticien (200 caractères au maximum).",
              example = "Master de psychologie clinique")
          String title,
      @Schema(
              description = "Établissement ou organisme émetteur ; null si non renseigné.",
              example = "Université de Lyon")
          String issuer,
      @Schema(
              description = "Année d'obtention, de 1950 à 2100 ; null si non renseignée.",
              example = "2012")
          Integer year,
      @Schema(
              description =
                  "Type MIME détecté par signature du fichier (jamais d'après l'extension)."
                      + " L'image est ré-encodée sans métadonnées, le PDF est conservé tel quel.",
              allowableValues = {"application/pdf", "image/jpeg", "image/png"},
              example = "application/pdf")
          String mime,
      @Schema(
              description = "Taille du fichier stocké, en octets (10 Mo au maximum).",
              example = "482133")
          int sizeBytes,
      @Schema(description = "Date-heure du dépôt, en UTC.", example = "2026-03-14T09:30:00Z")
          Instant uploadedAt,
      @Schema(
              description =
                  "Résultat de l'examen du justificatif par l'administration : pending = pas encore"
                      + " examiné, conform = conforme, non_conform = non conforme.",
              allowableValues = {"pending", "conform", "non_conform"},
              example = "pending")
          String reviewedStatus,
      @Schema(
              description =
                  "true si le fichier a été purgé du stockage (90 jours après la décision) : les"
                      + " métadonnées restent, mais l'URL et le fichier répondent 410"
                      + " DOCUMENT_PURGED.")
          boolean purged) {}

  /** Message reçu d'un administrateur ; la note interne n'est jamais exposée. */
  @Schema(
      description =
          "Décision de l'administration accompagnée d'un message au praticien. La note interne de"
              + " l'administrateur n'est jamais exposée.")
  record DecisionDto(
      @Schema(
              description =
                  "Nature de la décision : approved = dossier approuvé, needs_more = pièces"
                      + " complémentaires demandées, rejected = dossier refusé, suspended = compte"
                      + " suspendu, reactivated = compte réactivé, appeal_accepted = recours"
                      + " accepté, appeal_rejected = recours refusé, report_resolved = signalement"
                      + " traité.",
              allowableValues = {
                "approved",
                "needs_more",
                "rejected",
                "suspended",
                "reactivated",
                "appeal_accepted",
                "appeal_rejected",
                "report_resolved"
              },
              example = "needs_more")
          String kind,
      @Schema(
              description = "Message de l'administrateur au praticien.",
              example = "Merci de joindre un diplôme lisible.")
          String message,
      @Schema(description = "Date-heure de la décision, en UTC.", example = "2026-03-15T14:05:00Z")
          Instant decidedAt) {}

  @Schema(
      description =
          "Adresse du cabinet. En lecture, chaque champ vaut null s'il n'est pas renseigné ; en"
              + " écriture (PATCH /api/me/professional-info), un champ absent ou null est inchangé"
              + " et une chaîne vide l'efface.")
  record AddressDto(
      @Schema(
              description = "Numéro et nom de la rue (200 caractères au maximum) ; null si absent.",
              example = "12 rue de la Paix")
          String street,
      @Schema(
              description = "Code postal (200 caractères au maximum) ; null si absent.",
              example = "75002")
          String postalCode,
      @Schema(
              description = "Ville (200 caractères au maximum) ; null si absente.",
              example = "Paris")
          String city) {}

  @Schema(description = "Informations professionnelles du praticien, telles qu'enregistrées.")
  record ProfessionalInfoDto(
      @Schema(
              description = "Numéro professionnel (ADELI, RPPS ou autre) ; null si non renseigné.",
              example = "759912345")
          String professionalNumber,
      @Schema(
              description = "Téléphone, tel que saisi ; null si non renseigné.",
              example = "+33612345678")
          String phone,
      @Schema(description = "Adresse du cabinet ; ses champs valent null si absents.")
          AddressDto address,
      @Schema(
              description =
                  "true si le praticien exerce uniquement à distance (visio) : l'adresse n'est pas"
                      + " exigée.")
          boolean remoteOnly) {}

  /** Champs absents ou null : inchangés ; chaîne vide : champ effacé. */
  @Schema(
      description =
          "Mise à jour partielle des informations professionnelles : un champ absent ou null est"
              + " inchangé, une chaîne vide efface le champ, toute autre valeur est contrôlée.")
  record ProfessionalInfoRequest(
      @Schema(
              description =
                  "Numéro professionnel : 4 à 20 caractères parmi lettres, chiffres, espace et"
                      + " tiret. Vide : effacé.",
              example = "759912345")
          String professionalNumber,
      @Schema(
              description =
                  "Téléphone : 6 à 30 caractères parmi chiffres, + espace ( ) . et tiret. Vide :"
                      + " effacé.",
              example = "+33 6 12 34 56 78")
          String phone,
      @Schema(
              description =
                  "Adresse du cabinet, champ par champ (200 caractères au maximum chacun). Absente"
                      + " : adresse inchangée.")
          AddressDto address,
      @Schema(description = "true pour exercer uniquement à distance. Absent ou null : inchangé.")
          Boolean remoteOnly) {}

  @Schema(
      description =
          "URL signée, à courte durée, pour lire un justificatif déchiffré. Elle ne fonctionne"
              + " qu'avec la session du praticien propriétaire.")
  record SignedUrlDto(
      @Schema(
              description =
                  "Chemin relatif à appeler en GET (avec le cookie de session) : il porte les"
                      + " paramètres exp et sig. Valable 5 minutes.",
              example = "/api/me/verification/documents/9b4c0c7e/file?exp=1773480600&sig=ab12cd34")
          String url,
      @Schema(
              description =
                  "Date-heure d'expiration de l'URL, en UTC (5 minutes après l'émission).",
              example = "2026-03-14T09:35:00Z")
          Instant expiresAt) {}

  @Schema(
      description =
          "Dossier professionnel du praticien : état de la vérification, justificatifs actifs,"
              + " pièces manquantes et recommandées, décisions reçues et informations"
              + " professionnelles.")
  record VerificationDto(
      @Schema(
              description =
                  "État du dossier : email_pending = e-mail non confirmé ; docs_pending ="
                      + " justificatifs à déposer ; under_review = dossier soumis, en cours"
                      + " d'examen ; approved = praticien vérifié ; needs_more = pièces"
                      + " complémentaires demandées ; rejected = dossier refusé ; suspended ="
                      + " compte suspendu ; closed = compte clos. Le dossier n'est modifiable"
                      + " qu'en docs_pending et needs_more.",
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
              example = "docs_pending")
          String status,
      @Schema(
              description = "Profession du praticien : psychologist, psychiatrist ou coach.",
              allowableValues = {"psychologist", "psychiatrist", "coach"},
              example = "psychologist")
          String profession,
      @Schema(description = "Justificatifs actifs du dossier (20 au maximum), retirés exclus.")
          List<DocumentDto> documents,
      @Schema(
              description =
                  "Types de justificatifs obligatoires encore absents (diploma, identity) ; le"
                      + " dossier ne peut être soumis que si la liste est vide.",
              example = "diploma")
          List<String> missing,
      @Schema(
              description =
                  "Types de justificatifs conseillés encore absents (registration pour un"
                      + " psychologue ou un psychiatre, certification pour un coach) ; jamais"
                      + " bloquant.",
              example = "registration")
          List<String> recommended,
      @Schema(
              description =
                  "Date-heure de la dernière soumission du dossier, en UTC ; null s'il n'a jamais"
                      + " été soumis.",
              example = "2026-03-14T09:30:00Z")
          Instant submittedAt,
      @Schema(
              description =
                  "Décisions de l'administration accompagnées d'un message, de la plus récente à"
                      + " la plus ancienne.")
          List<DecisionDto> decisions,
      @Schema(description = "Informations professionnelles enregistrées.")
          ProfessionalInfoDto professionalInfo) {}
}
