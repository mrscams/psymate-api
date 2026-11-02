package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "AppealOtpRequiredDto",
    description =
        "Corps du 202 : le mot de passe est correct, la double authentification est active et le"
            + " code est absent ; rien n'a été enregistré, renvoyer la demande avec otp.")
public record OtpRequiredDto(
    @Schema(description = "Toujours vrai.", example = "true") boolean otpRequired) {}
