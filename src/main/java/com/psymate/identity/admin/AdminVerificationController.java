package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminDtos.DecisionDto;
import com.psymate.identity.admin.AdminDtos.DecisionRequest;
import com.psymate.identity.admin.AdminDtos.DocumentDto;
import com.psymate.identity.admin.AdminDtos.DossierDto;
import com.psymate.identity.admin.AdminDtos.QueueDto;
import com.psymate.identity.admin.AdminDtos.ReviewRequest;
import com.psymate.identity.admin.AdminDtos.SignedUrlDto;
import com.psymate.identity.api.CurrentPractitioner;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Validation des dossiers professionnels par l'administration (A10.4, fiche 20). Le rôle admin et
 * la 2FA sont contrôlés en amont par AdminTwoFactorFilter sur tout /api/admin/**.
 */
@RestController
@RequestMapping("/api/admin/verifications")
class AdminVerificationController {

  private final AdminVerificationService service;

  AdminVerificationController(AdminVerificationService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listAdminVerifications",
      summary = "File des dossiers à vérifier (under_review ou needs_more), pagination par curseur")
  QueueDto list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return service.list(status, sort, cursor, limit);
  }

  @GetMapping("/{practitionerId}")
  @Operation(
      operationId = "getAdminVerification",
      summary = "Dossier d'un praticien : identité, coordonnées, justificatifs, décisions")
  DossierDto get(@PathVariable UUID practitionerId) {
    return service.get(practitionerId);
  }

  @GetMapping("/{practitionerId}/documents/{docId}/url")
  @Operation(
      operationId = "getAdminDocumentUrl",
      summary = "URL signée de 5 minutes pour ouvrir un justificatif")
  SignedUrlDto getDocumentUrl(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @PathVariable UUID practitionerId,
      @PathVariable UUID docId) {
    return service.signedUrl(admin.id(), practitionerId, docId);
  }

  @GetMapping("/{practitionerId}/documents/{docId}/file")
  @Operation(
      operationId = "getAdminDocumentFile",
      summary = "Sert le justificatif déchiffré (URL signée) et consigne l'accès")
  ResponseEntity<byte[]> getDocumentFile(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @PathVariable UUID practitionerId,
      @PathVariable UUID docId,
      @RequestParam(required = false) Long exp,
      @RequestParam(required = false) String sig,
      HttpServletRequest request) {
    AdminVerificationService.FileContent f =
        service.file(admin.id(), practitionerId, docId, exp, sig, request.getRemoteAddr());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(f.mime()))
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .header("X-Content-Type-Options", "nosniff")
        .body(f.bytes());
  }

  @PatchMapping("/{practitionerId}/documents/{docId}")
  @Operation(
      operationId = "reviewAdminDocument",
      summary = "Qualifie un justificatif (pending, conform, non_conform)")
  DocumentDto reviewDocument(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @PathVariable UUID practitionerId,
      @PathVariable UUID docId,
      @RequestBody ReviewRequest body) {
    return service.review(admin.id(), practitionerId, docId, body.reviewedStatus());
  }

  @PostMapping("/{practitionerId}/decision")
  @Operation(
      operationId = "decideAdminVerification",
      summary = "Approuve, demande un complément ou refuse (décision append-only)")
  ResponseEntity<DecisionDto> decide(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @PathVariable UUID practitionerId,
      @RequestBody DecisionRequest body,
      HttpServletRequest request) {
    DecisionDto created = service.decide(admin, practitionerId, body, request.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }
}
