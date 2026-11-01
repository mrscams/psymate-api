package com.psymate.patients;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTO REST du module patients (camelCase). */
final class PatientDtos {

  private PatientDtos() {}

  @Schema(
      description =
          "Drapeau actif posé sur un patient (point d'attention clinique). Un drapeau levé n'est"
              + " plus renvoyé.")
  record FlagDto(
      @Schema(
              description =
                  "Nature du drapeau : watch = à surveiller (une raison est alors obligatoire),"
                      + " risk = risque, shared_care = suivi partagé avec un autre professionnel.",
              allowableValues = {"watch", "risk", "shared_care"},
              example = "watch")
          String kind,
      @Schema(
              description =
                  "Raison saisie par le praticien ; toujours renseignée pour watch, null pour risk"
                      + " et shared_care quand aucune raison n'a été saisie.",
              example = "Troubles du sommeil depuis trois semaines")
          String reason,
      @Schema(
              description = "Date-heure de pose du drapeau, UTC ISO 8601.",
              example = "2026-03-14T09:30:00Z")
          OffsetDateTime setAt) {}

  @Schema(description = "Tag libre du praticien associé à un patient.")
  record TagDto(
      @Schema(description = "Identifiant du tag.", example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID id,
      @Schema(description = "Libellé du tag, tel qu'affiché.", example = "sommeil") String label) {}

  @Schema(
      description =
          "Fiche d'un patient du praticien connecté, avec ses drapeaux actifs et ses tags."
              + " Chaque modification par PATCH incrémente currentVersion et archive un"
              + " instantané consultable via GET /api/patients/{id}/versions.")
  record PatientDto(
      @Schema(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID id,
      @Schema(
              description =
                  "Nom affiché dans toute l'application (obligatoire, espaces de bord retirés).",
              example = "Marie D.")
          String displayName,
      @Schema(description = "Prénom ; null si non renseigné.", example = "Marie") String firstName,
      @Schema(description = "Nom de famille ; null si non renseigné.", example = "Dupont")
          String lastName,
      @Schema(
              description =
                  "Date de naissance AAAA-MM-JJ ; null si non renseignée. Passée et de moins de"
                      + " 120 ans.",
              example = "1990-04-02")
          LocalDate birthDate,
      @Schema(description = "Pronom d'adresse libre ; null si non renseigné.", example = "elle")
          String pronoun,
      @Schema(
              description = "Téléphone, texte libre ; null si non renseigné.",
              example = "0102030405")
          String phone,
      @Schema(
              description = "Adresse e-mail, texte libre ; null si non renseignée.",
              example = "marie.d@example.org")
          String email,
      @Schema(
              description =
                  "Contexte de vie du patient : objet JSON libre dont les clés et valeurs sont"
                      + " définies par le client (par exemple activity). Objet vide si rien n'est"
                      + " renseigné.")
          Map<String, Object> context,
      @Schema(
              description =
                  "Cadre du suivi : objet JSON libre dont les clés et valeurs sont définies par le"
                      + " client (par exemple rhythm). Objet vide si rien n'est renseigné.")
          Map<String, Object> followUp,
      @Schema(description = "Drapeaux actifs, du plus ancien au plus récent ; liste vide si aucun.")
          List<FlagDto> flags,
      @Schema(description = "Tags du patient ; liste vide si aucun.") List<TagDto> tags,
      @Schema(
              description =
                  "Numéro de la version courante de la fiche : 1 à la création, +1 à chaque"
                      + " modification par PATCH.",
              example = "1")
          int currentVersion,
      @Schema(
              description =
                  "Date-heure d'archivage, UTC ISO 8601 ; null tant que le patient est actif.",
              example = "2026-03-14T09:30:00Z")
          OffsetDateTime archivedAt,
      @Schema(
              description = "Date-heure de création, UTC ISO 8601.",
              example = "2026-01-05T08:00:00Z")
          OffsetDateTime createdAt,
      @Schema(
              description = "Date-heure de dernière modification, UTC ISO 8601.",
              example = "2026-03-14T09:30:00Z")
          OffsetDateTime updatedAt) {}

  @Schema(description = "Page de patients, paginée par curseur opaque.")
  record PatientPage(
      @Schema(description = "Patients de la page, dans l'ordre du paramètre sort.")
          List<PatientDto> items,
      @Schema(
              description =
                  "Curseur opaque à renvoyer tel quel dans le paramètre cursor pour obtenir la page"
                      + " suivante ; null sur la dernière page.",
              example = "bzo1MA")
          String nextCursor) {}

  @Schema(
      description =
          "Instantané de la fiche après une modification par PATCH. La création (version 1) n'a"
              + " pas d'instantané : la liste commence à la version 2.")
  record VersionDto(
      @Schema(description = "Numéro de la version produite par la modification.", example = "2")
          int version,
      @Schema(
              description =
                  "État de la fiche après la modification : objet JSON avec displayName, firstName,"
                      + " lastName, birthDate (AAAA-MM-JJ ou null), pronoun, phone, email, context"
                      + " et followUp.")
          Map<String, Object> snapshot,
      @Schema(
              description =
                  "Motif de la modification fourni par le champ reason du PATCH ; null si absent.",
              example = "Changement de numéro")
          String reason,
      @Schema(
              description = "Identifiant du praticien auteur de la modification.",
              example = "5f1b7c3e-1d2a-4b6c-8e9f-0a1b2c3d4e5f")
          UUID authorId,
      @Schema(
              description = "Date-heure de la modification, UTC ISO 8601.",
              example = "2026-03-14T09:30:00Z")
          OffsetDateTime createdAt) {}

  @Schema(description = "Drapeau à poser sur un patient (à la création ou via POST /flags).")
  record FlagRequest(
      @Schema(
              description =
                  "Nature du drapeau : watch = à surveiller, risk = risque, shared_care = suivi"
                      + " partagé. Toute autre valeur est refusée (422 VALIDATION_FAILED).",
              allowableValues = {"watch", "risk", "shared_care"},
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "risk")
          String kind,
      @Schema(
              description =
                  "Raison libre ; obligatoire (non vide) pour watch, facultative pour risk et"
                      + " shared_care.",
              example = "Idées noires évoquées en séance")
          String reason) {}

  @Schema(description = "Liste complète des tags d'un patient (remplace la liste existante).")
  record TagsRequest(
      @Schema(
              description =
                  "Libellés de tags existants du praticien (à créer d'abord par POST /api/tags). Un"
                      + " libellé qui correspond à une émotion est refusé (TAG_IS_EMOTION), un"
                      + " libellé inconnu aussi (TAG_UNKNOWN). Liste vide pour retirer tous les"
                      + " tags ; les doublons sont fusionnés.")
          List<String> tags) {}

  @Schema(description = "Création d'un patient dans le cabinet du praticien connecté.")
  record CreatePatientRequest(
      @Schema(
              description = "Nom affiché ; obligatoire, non vide après suppression des espaces.",
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "Marie D.")
          String displayName,
      @Schema(description = "Prénom ; vide ou null = non renseigné.", example = "Marie")
          String firstName,
      @Schema(description = "Nom de famille ; vide ou null = non renseigné.", example = "Dupont")
          String lastName,
      @Schema(
              description =
                  "Date de naissance AAAA-MM-JJ ; doit être passée et de moins de 120 ans"
                      + " (422 VALIDATION_FAILED sinon).",
              example = "1990-04-02")
          LocalDate birthDate,
      @Schema(
              description = "Pronom d'adresse libre ; vide ou null = non renseigné.",
              example = "elle")
          String pronoun,
      @Schema(
              description = "Téléphone, texte libre ; vide ou null = non renseigné.",
              example = "0102030405")
          String phone,
      @Schema(
              description = "Adresse e-mail, texte libre ; vide ou null = non renseignée.",
              example = "marie.d@example.org")
          String email,
      @Schema(description = "Contexte de vie : objet JSON libre ; null = objet vide.")
          Map<String, Object> context,
      @Schema(description = "Cadre du suivi : objet JSON libre ; null = objet vide.")
          Map<String, Object> followUp,
      @Schema(
              description =
                  "Drapeaux à poser dès la création ; un seul par nature (409 FLAG_ALREADY_ACTIVE"
                      + " sinon) ; null = aucun.")
          List<FlagRequest> flags,
      @Schema(
              description =
                  "Libellés de tags existants à associer (mêmes règles que PUT"
                      + " /api/patients/{id}/tags) ; null = aucun.")
          List<String> tags) {}
}
