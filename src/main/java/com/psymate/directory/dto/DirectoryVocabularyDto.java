package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Listes fermées de l'annuaire. Seules les entrées actives sont rendues, dans l'ordre"
            + " d'affichage.")
public record DirectoryVocabularyDto(
    @Schema(
            description =
                "Spécialités actives, par rang puis libellé français (liste administrable).")
        List<Item> specialties,
    @Schema(
            description =
                "Publics accueillis actifs, par rang puis libellé français (liste"
                    + " administrable) : par exemple adults, young_adults, adolescents,"
                    + " children, couples_families.")
        List<Item> audiences,
    @Schema(
            description =
                "Modalités de consultation, liste fixe : in_person (au cabinet), video (en"
                    + " visio), home (à domicile).")
        List<Item> modalities,
    @Schema(
            description =
                "Langues proposées par défaut (15 codes ISO 639-1, triées par libellé"
                    + " français) ; tout autre code ISO 639-1 reste accepté dans le profil.")
        List<Item> languages) {}
