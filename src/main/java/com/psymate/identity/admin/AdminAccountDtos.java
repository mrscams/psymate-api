package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminDtos.DecisionItemDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Corps des réponses et requêtes de l'administration des comptes (M1-14b) : liste, détail,
 * suspension, réactivation, journal et recours. Jamais de hachage, de donnée 2FA, d'adresse IP ni
 * de donnée patient.
 */
final class AdminAccountDtos {

  private AdminAccountDtos() {}

  /** État de vérification : statut, depuis quand, et retard de traitement (under_review). */
  record VerificationDto(String status, Instant since, boolean overdue) {}

  record AccountItemDto(
      UUID id,
      String displayName,
      String email,
      String profession,
      String city,
      String role,
      VerificationDto verification,
      boolean twoFactorEnabled,
      Instant createdAt,
      Instant lastLoginAt,
      Instant suspendedAt,
      Instant purgeAt) {}

  /**
   * Comptes d'une page. counts : approved, under_review, needs_more, suspended, rejected, sur
   * l'ensemble des comptes qui respectent q, role et profession (le filtre status n'y entre pas,
   * pour que les onglets restent justes).
   */
  record AccountListDto(
      List<AccountItemDto> items, String nextCursor, Map<String, Integer> counts) {}

  record AppealSummaryDto(
      UUID id,
      UUID decisionId,
      Instant submittedAt,
      Instant reviewedAt,
      String outcome,
      String message,
      String responseMessage) {}

  record AccountDetailDto(
      UUID id,
      String displayName,
      String email,
      String profession,
      String city,
      String role,
      VerificationDto verification,
      boolean twoFactorEnabled,
      Instant createdAt,
      Instant lastLoginAt,
      Instant suspendedAt,
      Instant purgeAt,
      List<DecisionItemDto> decisions,
      List<AppealSummaryDto> appeals) {}

  record ReasonRequest(String reason) {}

  record SuspendDto(UUID id, String verificationStatus, Instant suspendedAt, UUID decisionId) {}

  record ReactivateDto(UUID id, String verificationStatus, UUID decisionId) {}

  // ---- journal

  record ActorDto(UUID id, String displayName) {}

  record JournalItemDto(
      UUID id,
      Instant at,
      String kind,
      String source,
      ActorDto actor,
      String actorType,
      UUID practitionerId,
      String practitionerDisplayName,
      String summary,
      Map<String, Object> details) {}

  record JournalDto(List<JournalItemDto> items, String nextCursor) {}

  // ---- recours

  record AppealRequest(String email, String password, String otp, String message) {}

  record AppealCreatedDto(UUID id, Instant submittedAt, String status) {}

  record OtpRequiredDto(boolean otpRequired) {}

  record AppealItemDto(
      UUID id,
      UUID practitionerId,
      String practitionerDisplayName,
      UUID decisionId,
      String decisionKind,
      UUID decisionAdminId,
      String message,
      Instant submittedAt,
      Instant reviewedAt,
      UUID reviewedBy,
      String outcome,
      String responseMessage) {}

  record AppealListDto(List<AppealItemDto> items, String nextCursor) {}

  record AppealDecisionRequest(String outcome, String messageToPractitioner) {}
}
