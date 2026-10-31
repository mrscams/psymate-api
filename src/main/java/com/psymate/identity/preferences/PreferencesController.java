package com.psymate.identity.preferences;

import com.psymate.identity.api.CurrentPractitioner;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Préférences par compte (A10.2, fiche 14) : objet plat, merge RFC 7396. */
@RestController
@RequestMapping("/api/me/preferences")
class PreferencesController {

  private final PreferencesService service;

  PreferencesController(PreferencesService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Préférences du compte (valeurs stockées complétées par les défauts)")
  Map<String, Object> getPreferences(@AuthenticationPrincipal CurrentPractitioner me) {
    return service.get(me.id());
  }

  @PatchMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, "application/merge-patch+json"})
  @Operation(
      summary =
          "Modifie les préférences (RFC 7396 : null = retour au défaut, clés inconnues ignorées)")
  Map<String, Object> updatePreferences(
      @AuthenticationPrincipal CurrentPractitioner me, @RequestBody Map<String, Object> patch) {
    return service.patch(me.id(), patch);
  }
}
