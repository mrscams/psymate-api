package com.psymate.identity.verification;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.verification.VerificationDtos.DocumentDto;
import com.psymate.identity.verification.VerificationDtos.ProfessionalInfoDto;
import com.psymate.identity.verification.VerificationDtos.ProfessionalInfoRequest;
import com.psymate.identity.verification.VerificationDtos.SignedUrlDto;
import com.psymate.identity.verification.VerificationDtos.VerificationDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Dossier professionnel du praticien connecté (A10.4, fiche 13). */
@RestController
@RequestMapping("/api/me")
class VerificationController {

  private final VerificationService service;

  VerificationController(VerificationService service) {
    this.service = service;
  }

  @GetMapping("/verification")
  @Operation(summary = "État du dossier professionnel, justificatifs, pièces manquantes")
  VerificationDto getVerification(@AuthenticationPrincipal CurrentPractitioner me) {
    return service.get(me.id());
  }

  @PostMapping(path = "/verification/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Dépose un justificatif (PDF, JPEG ou PNG, 10 Mo au maximum)")
  ResponseEntity<DocumentDto> uploadDocument(
      @AuthenticationPrincipal CurrentPractitioner me,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) String issuer,
      @RequestParam(required = false) String year,
      @RequestParam(required = false) MultipartFile file) {
    byte[] content = null;
    if (file != null) {
      try {
        content = file.getBytes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    DocumentDto created = service.upload(me.id(), type, title, issuer, year, content);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/verification/documents/{id}")
  @Operation(summary = "Métadonnées d'un justificatif (403 s'il appartient à un autre praticien)")
  DocumentDto getDocument(@AuthenticationPrincipal CurrentPractitioner me, @PathVariable UUID id) {
    return service.getDocument(me.id(), id);
  }

  @DeleteMapping("/verification/documents/{id}")
  @Operation(summary = "Retire un justificatif (docs_pending ou needs_more seulement)")
  ResponseEntity<Void> deleteDocument(
      @AuthenticationPrincipal CurrentPractitioner me, @PathVariable UUID id) {
    service.delete(me.id(), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/verification/documents/{id}/url")
  @Operation(summary = "URL signée de 5 minutes pour lire le justificatif déchiffré")
  SignedUrlDto getDocumentUrl(
      @AuthenticationPrincipal CurrentPractitioner me, @PathVariable UUID id) {
    return service.signedUrl(me.id(), id);
  }

  @GetMapping("/verification/documents/{id}/file")
  @Operation(summary = "Sert le justificatif déchiffré (URL signée, praticien propriétaire)")
  ResponseEntity<byte[]> getDocumentFile(
      @AuthenticationPrincipal CurrentPractitioner me,
      @PathVariable UUID id,
      @RequestParam(required = false) Long exp,
      @RequestParam(required = false) String sig,
      HttpServletRequest request) {
    VerificationService.FileContent f =
        service.file(me.id(), id, exp, sig, request.getRemoteAddr());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(f.mime()))
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .header("X-Content-Type-Options", "nosniff")
        .body(f.bytes());
  }

  @PostMapping("/verification/submit")
  @Operation(summary = "Soumet le dossier pour vérification (409 avec les pièces manquantes)")
  VerificationDto submitVerification(@AuthenticationPrincipal CurrentPractitioner me) {
    return service.submit(me.id());
  }

  @PatchMapping("/professional-info")
  @Operation(summary = "Met à jour n° professionnel, téléphone, adresse et exercice à distance")
  ProfessionalInfoDto updateProfessionalInfo(
      @AuthenticationPrincipal CurrentPractitioner me, @RequestBody ProfessionalInfoRequest r) {
    return service.updateProfessionalInfo(me.id(), r);
  }
}
