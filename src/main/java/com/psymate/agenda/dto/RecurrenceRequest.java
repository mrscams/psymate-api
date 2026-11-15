package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Répétition d'un rendez-vous à la création (G Q55, fiche 15b « Répéter ») : toutes les 1, 2 ou 4
 * semaines, par nombre d'occurrences ou jusqu'à une date, 26 occurrences au plus. Rien de cette
 * règle n'est stocké : chaque occurrence devient un rendez-vous indépendant, de même seriesId.
 * Exactement un de {@code count} et {@code until} est attendu (vérifié par le contrôleur : 422 sur
 * le champ recurrence, REQUIRED si aucun, INVALID_VALUE si les deux).
 */
@Schema(
    description =
        "Répétition hebdomadaire : toutes les everyWeeks semaines, soit count fois, soit jusqu'à"
            + " until inclus ; exactement un des deux (sinon 422 sur recurrence : REQUIRED si"
            + " aucun, INVALID_VALUE si les deux). 26 occurrences au plus (422"
            + " TOO_MANY_OCCURRENCES). Chaque occurrence garde l'heure locale du premier"
            + " rendez-vous dans le fuseau tz, même après un changement d'heure ; aucun décalage"
            + " sur jour férié.")
public record RecurrenceRequest(
    @Schema(
            description = "Intervalle en semaines : 1, 2 ou 4 (INVALID_VALUE sinon). Obligatoire.",
            example = "1")
        @NotNull
        Integer everyWeeks,
    @Schema(
            description =
                "Nombre d'occurrences, premier rendez-vous compris : 1 à 26 (au-delà, 422"
                    + " TOO_MANY_OCCURRENCES). Exclusif de until.",
            minimum = "1",
            example = "4")
        @Min(1)
        Integer count,
    @Schema(
            description =
                "Dernier jour possible d'une occurrence, inclus, date ISO 8601 (AAAA-MM-JJ, par"
                    + " exemple 2026-12-28) lue dans le fuseau tz ; pas avant le jour du premier"
                    + " rendez-vous (INVALID_VALUE). Exclusif de count.")
        LocalDate until) {}
