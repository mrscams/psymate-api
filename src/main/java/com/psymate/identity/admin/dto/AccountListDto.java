package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Comptes d'une page. counts : approved, under_review, needs_more, suspended, rejected, sur
 * l'ensemble des comptes qui respectent q, role et profession (le filtre status n'y entre pas, pour
 * que les onglets restent justes).
 */
@Schema(
    description =
        "Page de comptes, triés du plus récemment créé au plus ancien, avec le nombre de comptes"
            + " par statut.")
public record AccountListDto(
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
