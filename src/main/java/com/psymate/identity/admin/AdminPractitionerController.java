package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminAccountDtos.AccountDetailDto;
import com.psymate.identity.admin.AdminAccountDtos.AccountListDto;
import com.psymate.identity.admin.AdminAccountDtos.ReactivateDto;
import com.psymate.identity.admin.AdminAccountDtos.ReasonRequest;
import com.psymate.identity.admin.AdminAccountDtos.SuspendDto;
import com.psymate.identity.api.CurrentPractitioner;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comptes des praticiens pour l'administration (fiche 21, M1-14b). Le rôle admin et la 2FA sont
 * contrôlés en amont par AdminTwoFactorFilter sur tout /api/admin/**.
 */
@RestController
@RequestMapping("/api/admin/practitioners")
class AdminPractitionerController {

  private final AdminPractitionerService service;

  AdminPractitionerController(AdminPractitionerService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listAdminPractitioners",
      summary =
          "Comptes des praticiens (recherche q, filtres status, role, profession), pagination par"
              + " curseur, compteurs par statut")
  AccountListDto list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) String profession,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return service.list(q, status, role, profession, cursor, limit);
  }

  @GetMapping("/{id}")
  @Operation(
      operationId = "getAdminPractitioner",
      summary = "Compte d'un praticien : état, décisions et recours")
  AccountDetailDto get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/suspend")
  @Operation(
      operationId = "suspendAdminPractitioner",
      summary =
          "Suspend un compte (motif d'au moins 20 caractères) : sessions révoquées, e-mail au"
              + " praticien, fiche publique dépubliée")
  SuspendDto suspend(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @PathVariable UUID id,
      @RequestBody ReasonRequest body,
      HttpServletRequest request) {
    return service.suspend(admin, id, body.reason(), request.getRemoteAddr());
  }

  @PostMapping("/{id}/reactivate")
  @Operation(
      operationId = "reactivateAdminPractitioner",
      summary = "Réactive un compte suspendu ou refusé (motif d'au moins 20 caractères)")
  ReactivateDto reactivate(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @PathVariable UUID id,
      @RequestBody ReasonRequest body,
      HttpServletRequest request) {
    return service.reactivate(admin, id, body.reason(), request.getRemoteAddr());
  }
}
