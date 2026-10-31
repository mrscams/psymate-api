package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminAccountDtos.JournalDto;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Journal d'administration (fiche 22, M1-14b) : lecture seule, aucune route d'écriture (405). Le
 * rôle admin et la 2FA sont contrôlés en amont par AdminTwoFactorFilter sur tout /api/admin/**.
 */
@RestController
@RequestMapping("/api/admin/journal")
class AdminJournalController {

  private final AdminJournalService service;

  AdminJournalController(AdminJournalService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listAdminJournal",
      summary =
          "Journal d'administration (décisions et événements, plus récents d'abord), pagination"
              + " par curseur ; jamais d'adresse IP")
  JournalDto list(
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) String practitionerId,
      @RequestParam(required = false) String actorId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return service.list(kind, practitionerId, actorId, from, to, cursor, limit);
  }
}
