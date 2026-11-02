package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Page de la file de vérification. Tri par date de soumission (à défaut dernière mise à"
            + " jour du compte), selon le paramètre sort.")
public record QueueDto(
    @Schema(description = "Dossiers de la page, dans l'ordre du tri demandé.")
        List<QueueItemDto> items,
    @Schema(
            description =
                "Curseur opaque à renvoyer dans le paramètre cursor pour la page suivante ; null"
                    + " sur la dernière page.")
        String nextCursor) {}
