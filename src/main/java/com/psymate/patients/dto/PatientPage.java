package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Page de patients, paginée par curseur opaque.")
public record PatientPage(
    @Schema(description = "Patients de la page, dans l'ordre du paramètre sort.")
        List<PatientDto> items,
    @Schema(
            description =
                "Curseur opaque à renvoyer tel quel dans le paramètre cursor pour obtenir la page"
                    + " suivante ; null sur la dernière page.",
            example = "bzo1MA")
        String nextCursor) {}
