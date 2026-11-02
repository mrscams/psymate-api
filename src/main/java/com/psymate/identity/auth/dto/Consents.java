package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** Consentements obligatoires (CGU et politique de confidentialité). */
@Schema(
    description =
        "Consentements obligatoires à l'inscription : les deux doivent valoir true (422"
            + " INVALID_FIELD sinon). Ils sont consignés dans le journal de sécurité.")
public record Consents(
    @Schema(description = "Acceptation des conditions générales d'utilisation ; true exigé.")
        @NotNull
        @AssertTrue
        Boolean terms,
    @Schema(description = "Acceptation de la politique de confidentialité ; true exigé.")
        @NotNull
        @AssertTrue
        Boolean privacy) {}
