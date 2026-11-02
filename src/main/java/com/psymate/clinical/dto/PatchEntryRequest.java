package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

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
public record PatchEntryRequest(
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
