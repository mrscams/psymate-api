package com.psymate.vocabulary.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Type d'entrée (note, event, situation, feeling, session, medication). */
@Schema(
    description =
        "Type d'entrée clinique du catalogue (six types en V1 : note, event, situation, feeling,"
            + " session, medication).")
public record EntryTypeView(
    @Schema(
            description = "Code stable du type d'entrée.",
            allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
            example = "feeling")
        String code,
    @Schema(description = "Libellé français affiché.", example = "Ressenti") String labelFr,
    @Schema(description = "Libellé anglais.", example = "Feeling") String labelEn,
    @Schema(
            description =
                "Nom de la table de détail propre au type (entry_feeling, entry_session,"
                    + " entry_medication) ; null pour les types sans détail (note, event,"
                    + " situation).",
            example = "entry_feeling")
        String detailTable,
    @Schema(
            description =
                "true si le type porte une durée (situation, session, medication), false sinon.",
            example = "false")
        boolean hasDuration,
    @Schema(
            description = "Rang d'affichage, croissant ; la liste est déjà triée dans cet ordre.",
            example = "4")
        int sortOrder) {}
