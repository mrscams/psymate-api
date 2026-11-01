package com.psymate.clinical.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO REST du module clinical (camelCase). Les requêtes n'ont ni authorType ni submissionId (A11) :
 * ces champs sont refusés avant la désérialisation ({@link ForbiddenFieldsAdvice}).
 */
final class EntryDtos {

  private EntryDtos() {}

  // ---- marqueurs des corps de requête contrôlés par ForbiddenFieldsAdvice

  /** Corps de requête du module clinical : authorType et submissionId y sont refusés. */
  interface ClinicalRequest {}

  // ---- réponses

  @Schema(
      description =
          "Moment auquel l'entrée a eu lieu, avec sa précision. Les bornes sont en UTC. Pour une"
              + " précision autre qu'exact, les bornes sont celles de la période désignée (jour,"
              + " mois, année entiers).")
  record EntryOccurredDto(
      @Schema(
              description = "Début de l'occurrence, date-heure UTC ISO 8601.",
              example = "2026-10-20T19:00:00Z")
          Instant from,
      @Schema(
              description =
                  "Fin de l'occurrence, date-heure UTC ISO 8601. Null pour une situation en cours"
                      + " (sans fin).",
              example = "2026-10-20T19:00:00Z")
          Instant to,
      @Schema(
              description =
                  "Précision de la date : exact = date-heure connue ; day = jour connu ; month ="
                      + " mois connu ; year = année connue ; period = période libre avec libellé ;"
                      + " life_phase = phase de vie (bornes déduites de la date de naissance du"
                      + " patient).",
              allowableValues = {"exact", "day", "month", "year", "period", "life_phase"},
              example = "exact")
          String precision,
      @Schema(
              description =
                  "Libellé de la période ou de la phase de vie (precision period ou life_phase)."
                      + " Null sinon.",
              example = "enfance")
          String label) {}

  @Schema(description = "Détail d'un ressenti (type feeling).")
  record EntryFeelingDto(
      @Schema(
              description = "Code de l'émotion, issu du catalogue de vocabulaire du praticien.",
              example = "anxiete")
          String emotionCode,
      @Schema(
              description = "Famille de l'émotion d'après le catalogue. Null si elle est inconnue.",
              example = "peur")
          String family,
      @Schema(description = "Intensité du ressenti, de 0 à 10.", minimum = "0", maximum = "10")
          Integer intensity,
      @Schema(
              description =
                  "Valence copiée de l'émotion au moment de la saisie. Null si l'émotion n'en a"
                      + " pas. Jamais fournie par l'appelant.")
          Integer valence,
      @Schema(
              description =
                  "Activation (arousal) copiée de l'émotion au moment de la saisie. Null si"
                      + " l'émotion n'en a pas. Jamais fournie par l'appelant.")
          Integer arousal) {}

  @Schema(description = "Détail d'un médicament (type medication).")
  record EntryMedicationDto(
      @Schema(description = "Molécule ou nom du médicament.", example = "sertraline")
          String molecule,
      @Schema(description = "Posologie, texte libre.", example = "50 mg") String dosage,
      @Schema(description = "Fréquence de prise, texte libre.", example = "1 fois par jour")
          String frequency,
      @Schema(description = "Prescripteur, texte libre.", example = "Dr Martin") String prescriber,
      @Schema(
              description = "Début de la période de prise, date ISO 8601 (AAAA-MM-JJ).",
              example = "2026-09-01")
          LocalDate periodFrom,
      @Schema(
              description =
                  "Fin de la période de prise, date ISO 8601 (AAAA-MM-JJ). Null si en cours.",
              example = "2026-12-01")
          LocalDate periodTo) {}

  @Schema(description = "Détail d'une séance (type session).")
  record EntrySessionDto(
      @Schema(
              description =
                  "Genre de séance : intake = séance d'accueil créée avec le patient ; session ="
                      + " séance ordinaire ; contact = contact hors rendez-vous (téléphone,"
                      + " e-mail) consigné après coup.",
              allowableValues = {"intake", "session", "contact"},
              example = "session")
          String kind,
      @Schema(
              description = "Modalité de la séance.",
              allowableValues = {"in_person", "video", "phone", "email"},
              example = "video")
          String modality,
      @Schema(
              description =
                  "Durée en minutes (1 à 600). Null tant que la séance n'est pas clôturée, ou si le"
                      + " contact n'en a pas.",
              minimum = "1",
              maximum = "600")
          Integer durationMin,
      @Schema(
              description = "Instant de clôture UTC. Null tant que la séance est ouverte.",
              example = "2026-10-20T20:00:00Z")
          Instant closedAt) {}

  /**
   * Entrée complète. {@code authorType} est en lecture seule et vaut toujours « practitioner » en
   * V1 (A11). {@code links} n'est renseigné que dans la réponse de création (POST, liste vide si
   * aucun lien demandé) ; il vaut null sur GET, PATCH et restauration, où les liens se lisent par
   * GET /api/entries/{id}/links (M2-01). {@code linkCount} est le nombre de liens acceptés.
   */
  @Schema(
      description =
          "Entrée clinique complète (version courante, détail selon le type, tags). Une entrée"
              + " archivée reste lisible : archivedAt est alors renseigné. Les liens se lisent par"
              + " GET /api/entries/{id}/links ; seule la réponse de création les porte dans links.")
  record EntryDto(
      @Schema(description = "Identifiant de l'entrée.") UUID id,
      @Schema(description = "Identifiant du patient du dossier.") UUID patientId,
      @Schema(
              description = "Type de l'entrée.",
              allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
              example = "note")
          String type,
      @Schema(
              description =
                  "Statut : draft = brouillon ; final = définitive. L'archivage n'est pas un"
                      + " statut, il est porté par archivedAt.",
              allowableValues = {"draft", "final"},
              example = "final")
          String status,
      @Schema(
              description =
                  "Visibilité : private = visible du seul praticien auteur, exclue de l'export ;"
                      + " clinical = partagée avec l'équipe clinique.",
              allowableValues = {"private", "clinical"},
              example = "clinical")
          String visibility,
      @Schema(
              description =
                  "Provenance de l'entrée. Lecture seule : vaut toujours practitioner en V1, jamais"
                      + " acceptée en écriture (422 UNKNOWN_FIELD).",
              accessMode = Schema.AccessMode.READ_ONLY,
              allowableValues = {"practitioner"},
              example = "practitioner")
          String authorType,
      @Schema(description = "Moment auquel l'entrée a eu lieu, avec sa précision.")
          EntryOccurredDto occurred,
      @Schema(
              description = "Instant d'enregistrement de l'entrée, UTC ISO 8601.",
              example = "2026-10-20T19:05:00Z")
          Instant recordedAt,
      @Schema(
              description =
                  "Identifiant de la séance à laquelle l'entrée appartient. Null pour une séance"
                      + " (une séance n'est rattachée à aucune séance).")
          UUID sessionId,
      @Schema(
              description =
                  "Numéro de la version courante, à partir de 1. Incrémenté à chaque correction"
                      + " hors fenêtre de grâce de 1 h 15.",
              minimum = "1")
          int version,
      @Schema(description = "Titre de la version courante. Null si la version n'en a pas.")
          String title,
      @Schema(description = "Texte de la version courante. Null si la version n'en a pas.")
          String body,
      @Schema(
              description =
                  "Libellés des tags actifs de l'entrée, triés sans tenir compte de la casse."
                      + " Liste vide s'il n'y en a pas.")
          List<String> tags,
      @Schema(description = "Détail du ressenti. Renseigné pour le type feeling, null sinon.")
          EntryFeelingDto feeling,
      @Schema(description = "Détail du médicament. Renseigné pour le type medication, null sinon.")
          EntryMedicationDto medication,
      @Schema(description = "Détail de la séance. Renseigné pour le type session, null sinon.")
          EntrySessionDto session,
      @Schema(description = "Nombre de liens acceptés portant sur l'entrée.", minimum = "0")
          int linkCount,
      @Schema(
              description =
                  "Liens posés à la création, dans leur sens réel. Renseigné uniquement dans la"
                      + " réponse 201 de POST /api/patients/{id}/entries (liste vide si aucun lien"
                      + " demandé) ; null partout ailleurs (utiliser GET /api/entries/{id}/links).")
          List<EntryLinkDto> links,
      @Schema(
              description = "Instant d'archivage UTC. Null si l'entrée n'est pas archivée.",
              example = "2026-10-21T08:00:00Z")
          Instant archivedAt) {}

  /**
   * Lien posé à la création, dans son sens réel (source vers cible). {@code label} est le verbe vu
   * depuis la nouvelle entrée : libellé inverse si elle est la cible d'un type dirigé ; anglais si
   * Accept-Language: en, français sinon.
   */
  @Schema(
      description =
          "Lien accepté posé avec une nouvelle entrée, dans son sens réel (source vers cible).")
  record EntryLinkDto(
      @Schema(description = "Identifiant du lien.") UUID id,
      @Schema(description = "Entrée source du lien.") UUID sourceId,
      @Schema(description = "Entrée cible du lien.") UUID targetId,
      @Schema(description = "Code du type de lien.", example = "triggers") String linkType,
      @Schema(
              description =
                  "Verbe du lien vu depuis la nouvelle entrée : libellé inverse si elle est la"
                      + " cible d'un type dirigé. En anglais si Accept-Language vaut en, en"
                      + " français sinon.",
              example = "déclenche")
          String label) {}

  @Schema(
      description =
          "Une version d'une entrée. La liste est ordonnée de la plus récente à la plus ancienne.")
  record EntryVersionDto(
      @Schema(description = "Numéro de la version, à partir de 1.", minimum = "1") int version,
      @Schema(description = "Titre de cette version. Null s'il n'y en a pas.") String title,
      @Schema(description = "Texte de cette version. Null s'il n'y en a pas.") String body,
      @Schema(
              description =
                  "Motif de la correction (champ reason du PATCH). Null pour la version 1 ou si"
                      + " aucun motif n'a été donné.",
              example = "précision apportée en séance")
          String reason,
      @Schema(
              description =
                  "Provenance de l'entrée. Lecture seule : vaut toujours practitioner en V1.",
              accessMode = Schema.AccessMode.READ_ONLY,
              allowableValues = {"practitioner"},
              example = "practitioner")
          String authorType,
      @Schema(description = "Identifiant du praticien auteur de cette version.") UUID authorId,
      @Schema(
              description = "Instant de création de la version, UTC ISO 8601.",
              example = "2026-10-20T19:05:00Z")
          Instant createdAt) {}

  // ---- requêtes d'entrée

  @Schema(
      description =
          "Moment auquel l'entrée a eu lieu. Remplacé en entier lors d'un PATCH. Absent à la"
              + " création : l'instant de saisie est utilisé (sauf pour une situation, où il est"
              + " obligatoire).")
  record EntryOccurredRequest(
      @Schema(
              description =
                  "Début de l'occurrence, date-heure UTC ISO 8601. Obligatoire (REQUIRED) sauf"
                      + " pour la précision life_phase.",
              example = "2026-10-20T19:00:00Z")
          Instant from,
      @Schema(
              description =
                  "Fin de l'occurrence, date-heure UTC ISO 8601, jamais avant from (INVALID_TIME"
                      + " sur occurred.to). Absente : pour une situation, elle est en cours ;"
                      + " pour les autres types, égale à from (la précision period exige une"
                      + " fin).",
              example = "2026-10-20T20:00:00Z")
          Instant to,
      @Schema(
              description =
                  "Précision de la date : exact (défaut) = date-heure connue ; day = jour connu ;"
                      + " month = mois connu ; year = année connue ; period = période libre"
                      + " (libellé obligatoire) ; life_phase = phase de vie reconnue par son"
                      + " libellé (enfance), bornes déduites de la date de naissance du patient."
                      + " Toute autre valeur : INVALID_VALUE sur occurred.precision.",
              allowableValues = {"exact", "day", "month", "year", "period", "life_phase"},
              defaultValue = "exact",
              example = "exact")
          String precision,
      @Schema(
              description =
                  "Libellé de la période (precision period) ou de la phase de vie (precision"
                      + " life_phase).",
              example = "enfance")
          String label) {}

  @Schema(
      description =
          "Détail d'un ressenti. À la création, les deux champs sont obligatoires pour le type"
              + " feeling. En PATCH, un champ absent est conservé.")
  record EntryFeelingRequest(
      @Schema(
              description =
                  "Code d'une émotion active du catalogue du praticien. Inconnu : UNKNOWN_EMOTION"
                      + " sur feeling.emotionCode.",
              example = "anxiete")
          String emotionCode,
      @Schema(
              description = "Intensité du ressenti, de 0 à 10 (OUT_OF_RANGE sinon).",
              minimum = "0",
              maximum = "10",
              example = "7")
          Integer intensity) {}

  @Schema(
      description =
          "Détail d'un médicament. À la création, molecule est obligatoire pour le type"
              + " medication. En PATCH, un champ absent est conservé.")
  record EntryMedicationRequest(
      @Schema(description = "Molécule ou nom du médicament.", example = "sertraline")
          String molecule,
      @Schema(description = "Posologie, texte libre.", example = "50 mg") String dosage,
      @Schema(description = "Fréquence de prise, texte libre.", example = "1 fois par jour")
          String frequency,
      @Schema(description = "Prescripteur, texte libre.", example = "Dr Martin") String prescriber,
      @Schema(
              description = "Début de la période de prise, date ISO 8601 (AAAA-MM-JJ).",
              example = "2026-09-01")
          LocalDate periodFrom,
      @Schema(
              description = "Fin de la période de prise, date ISO 8601 (AAAA-MM-JJ).",
              example = "2026-12-01")
          LocalDate periodTo) {}

  @Schema(
      description =
          "Détail d'une séance. À la création d'une entrée de type session, seule la modalité est"
              + " acceptée (la durée se donne à la clôture : NOT_ALLOWED_ON_CREATE sur"
              + " session.durationMin). En PATCH, un champ absent est conservé ; le genre et la"
              + " clôture ne se modifient pas.")
  record EntrySessionRequest(
      @Schema(
              description = "Modalité de la séance (obligatoire pour le type session).",
              allowableValues = {"in_person", "video", "phone", "email"},
              example = "video")
          String modality,
      @Schema(
              description = "Durée en minutes, de 1 à 600 (OUT_OF_RANGE sinon).",
              minimum = "1",
              maximum = "600",
              example = "50")
          Integer durationMin) {}

  /**
   * Lien demandé avec une nouvelle entrée. {@code direction} : outgoing (défaut, la nouvelle entrée
   * est la source) ou incoming (elle est la cible).
   */
  @Schema(
      description =
          "Lien à poser avec une nouvelle entrée, accepté en même temps qu'elle (tout ou rien).")
  record EntryLinkRequest(
      @Schema(description = "Entrée à lier. Obligatoire (REQUIRED).") UUID targetId,
      @Schema(
              description =
                  "Code du type de lien. Obligatoire (REQUIRED). Inconnu : UNKNOWN_LINK_TYPE ; non"
                      + " permis pour ce couple d'entrées : LINK_TYPE_NOT_ALLOWED (la réponse"
                      + " porte alors allowedLinkTypes).",
              example = "triggers")
          String linkType,
      @Schema(
              description =
                  "Sens vu depuis la nouvelle entrée : outgoing (défaut) = elle est la source ;"
                      + " incoming = elle est la cible. Autre valeur : INVALID_VALUE.",
              allowableValues = {"outgoing", "incoming"},
              defaultValue = "outgoing",
              example = "outgoing")
          String direction) {}

  /**
   * Création : le détail fourni doit correspondre au type. {@code links} pose des liens manuels
   * acceptés avec l'entrée, tout ou rien (M2-02) ; refusé pour une séance.
   */
  @Schema(
      description =
          "Création d'une entrée. Champs minimaux par type : note = body ; event = title ;"
              + " situation = title et occurred.from ; feeling = feeling.emotionCode et"
              + " feeling.intensity ; medication = medication.molecule ; session ="
              + " session.modality. Un bloc de détail d'un autre type est refusé"
              + " (DETAIL_NOT_ALLOWED). authorType et submissionId sont refusés (UNKNOWN_FIELD)."
              + " Un corps de type session démarre une séance (une seule ouverte à la fois).")
  record CreateEntryRequest(
      @Schema(
              description = "Type de l'entrée. Obligatoire (REQUIRED).",
              allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "note")
          String type,
      @Schema(
              description =
                  "Statut : draft ou final (défaut final). Autre valeur : INVALID_VALUE. Ignoré"
                      + " pour une séance, toujours finale.",
              allowableValues = {"draft", "final"},
              defaultValue = "final",
              example = "final")
          String status,
      @Schema(
              description =
                  "Moment auquel l'entrée a eu lieu. Absent : l'instant de saisie (obligatoire"
                      + " pour une situation).")
          EntryOccurredRequest occurred,
      @Schema(
              description =
                  "Séance de rattachement, qui doit être une séance de ce patient (SESSION_INVALID"
                      + " sinon). Absent : la séance courante du patient (SESSION_REQUIRED s'il"
                      + " n'en a aucune). Interdit pour une séance (SESSION_NOT_ALLOWED).")
          UUID sessionId,
      @Schema(
              description =
                  "Visibilité : private ou clinical (défaut clinical). Autre valeur :"
                      + " INVALID_VALUE.",
              allowableValues = {"private", "clinical"},
              defaultValue = "clinical",
              example = "clinical")
          String visibility,
      @Schema(
              description =
                  "Titre (obligatoire pour event et situation). Refusé pour une séance"
                      + " (NOT_ALLOWED_ON_CREATE).",
              example = "Dispute avec le collègue")
          String title,
      @Schema(
              description =
                  "Texte (obligatoire pour note). Refusé pour une séance (NOT_ALLOWED_ON_CREATE) :"
                      + " le compte rendu se donne à la clôture.")
          String body,
      @Schema(
              description =
                  "Libellés de tags existants du praticien (UNKNOWN_TAG sur tags[i] sinon). Refusé"
                      + " pour une séance (NOT_ALLOWED_ON_CREATE).")
          List<String> tags,
      @Schema(description = "Détail du ressenti, pour le type feeling uniquement.")
          EntryFeelingRequest feeling,
      @Schema(description = "Détail du médicament, pour le type medication uniquement.")
          EntryMedicationRequest medication,
      @Schema(description = "Détail de la séance, pour le type session uniquement.")
          EntrySessionRequest session,
      @Schema(
              description =
                  "Liens à poser avec l'entrée, 20 au plus (OUT_OF_RANGE au-delà), tout ou rien :"
                      + " un lien refusé annule la création. Refusé pour une séance"
                      + " (NOT_ALLOWED_ON_CREATE).")
          List<EntryLinkRequest> links)
      implements ClinicalRequest {}

  /**
   * Modification partielle : un champ absent est inchangé ; {@code occurred} et {@code tags} sont
   * remplacés en entier ; un détail fourni est fusionné champ par champ avec le détail courant.
   */
  @Schema(
      description =
          "Correction partielle d'une entrée : un champ absent est inchangé ; occurred et tags sont"
              + " remplacés en entier ; un bloc de détail est fusionné champ par champ avec le"
              + " détail courant et doit correspondre au type de l'entrée (DETAIL_NOT_ALLOWED"
              + " sinon). Dans la fenêtre de grâce de 1 h 15 la modification est faite en place,"
              + " sinon elle crée une nouvelle version. Le type ne se modifie pas. authorType et"
              + " submissionId sont refusés (UNKNOWN_FIELD), links aussi (NOT_SUPPORTED_ON_PATCH).")
  record PatchEntryRequest(
      @Schema(
              description = "Nouveau statut : draft ou final. Autre valeur : INVALID_VALUE.",
              allowableValues = {"draft", "final"})
          String status,
      @Schema(description = "Nouveau moment de l'entrée, remplacé en entier.")
          EntryOccurredRequest occurred,
      @Schema(
              description =
                  "Nouvelle visibilité : private ou clinical. Autre valeur : INVALID_VALUE.",
              allowableValues = {"private", "clinical"})
          String visibility,
      @Schema(
              description =
                  "Nouveau titre. Un event ou une situation ne peut pas perdre son titre"
                      + " (REQUIRED).")
          String title,
      @Schema(description = "Nouveau texte. Une note ne peut pas perdre son texte (REQUIRED).")
          String body,
      @Schema(
              description =
                  "Nouveaux libellés de tags, remplaçant la liste actuelle (UNKNOWN_TAG sur tags[i]"
                      + " si un libellé n'existe pas). Un changement de tags seul ne crée jamais de"
                      + " version.")
          List<String> tags,
      @Schema(description = "Détail du ressenti à fusionner (entrée de type feeling).")
          EntryFeelingRequest feeling,
      @Schema(description = "Détail du médicament à fusionner (entrée de type medication).")
          EntryMedicationRequest medication,
      @Schema(description = "Détail de la séance à fusionner (entrée de type session).")
          EntrySessionRequest session,
      @Schema(
              description =
                  "Motif de la correction, conservé sur la nouvelle version. Sans effet quand la"
                      + " modification est faite en place pendant la fenêtre de grâce.",
              example = "précision apportée en séance")
          String reason)
      implements ClinicalRequest {}

  // ---- requêtes de séance

  @Schema(
      description =
          "Démarrage d'une séance maintenant. authorType et submissionId sont refusés"
              + " (UNKNOWN_FIELD).")
  record StartSessionRequest(
      @Schema(
              description = "Modalité de la séance. Obligatoire.",
              allowableValues = {"in_person", "video", "phone", "email"},
              example = "in_person")
          @NotBlank
          @Pattern(regexp = "in_person|video|phone|email")
          String modality)
      implements ClinicalRequest {}

  @Schema(
      description =
          "Clôture d'une séance ouverte. authorType et submissionId sont refusés (UNKNOWN_FIELD).")
  record CloseSessionRequest(
      @Schema(
              description =
                  "Durée de la séance en minutes, de 1 à 600 (OUT_OF_RANGE sinon). Absente : la"
                      + " durée par défaut du praticien, sinon 60 minutes.",
              minimum = "1",
              maximum = "600",
              example = "50")
          @Min(1)
          @Max(600)
          Integer durationMin,
      @Schema(
              description =
                  "Compte rendu de la séance, texte libre. Vide ou absent : aucun compte rendu.")
          String summary)
      implements ClinicalRequest {}

  @Schema(
      description =
          "Contact hors rendez-vous (téléphone, e-mail), consigné comme une séance déjà clôturée."
              + " authorType et submissionId sont refusés (UNKNOWN_FIELD).")
  record RecordContactRequest(
      @Schema(
              description = "Modalité du contact. Obligatoire.",
              allowableValues = {"phone", "email"},
              example = "phone")
          @NotBlank
          @Pattern(regexp = "phone|email")
          String modality,
      @Schema(
              description = "Date-heure du contact, UTC ISO 8601. Obligatoire.",
              example = "2026-10-22T08:30:00Z")
          @NotNull
          Instant occurredFrom,
      @Schema(description = "Texte du contact, qui devient le compte rendu. Obligatoire.") @NotBlank
          String body)
      implements ClinicalRequest {}
}
