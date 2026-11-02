package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Page de timeline : triée par occurred_from décroissant, nextCursor nul sur la dernière page,
 * situations en cours qui chevauchent la fenêtre à part.
 */
@Schema(
    description =
        "Page de chronologie, triée par début d'occurrence décroissant (puis identifiant"
            + " décroissant), avec facettes de la fenêtre et situations en cours.")
public record TimelinePageDto(
    @Schema(description = "Entrées de la page, de la plus récente à la plus ancienne.")
        List<TimelineItemDto> items,
    @Schema(
            description =
                "Curseur opaque à renvoyer tel quel dans le paramètre cursor pour obtenir la page"
                    + " suivante. Null sur la dernière page.")
        String nextCursor,
    @Schema(description = "Facettes de toute la fenêtre filtrée.") TimelineFacetsDto facets,
    @Schema(
            description =
                "Situations non archivées dont la plage chevauche la fenêtre, y compris celles"
                    + " commencées avant from et toujours en cours. Seuls les filtres de"
                    + " fenêtre, de brouillon et de visibilité s'appliquent (pas types, emotions"
                    + " ni tags) ; indépendantes de la pagination.")
        List<TimelineItemDto> ongoingSituations) {}
