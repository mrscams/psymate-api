package com.psymate.identity.admin;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.admin.AdminAccountDtos.AccountDetailDto;
import com.psymate.identity.admin.AdminAccountDtos.AccountItemDto;
import com.psymate.identity.admin.AdminAccountDtos.AccountListDto;
import com.psymate.identity.admin.AdminAccountDtos.AppealSummaryDto;
import com.psymate.identity.admin.AdminAccountDtos.ReactivateDto;
import com.psymate.identity.admin.AdminAccountDtos.SuspendDto;
import com.psymate.identity.admin.AdminAccountDtos.VerificationDto;
import com.psymate.identity.admin.AdminCursors.Position;
import com.psymate.identity.admin.AdminDtos.DecisionItemDto;
import com.psymate.identity.admin.AdminException.FieldError;
import com.psymate.identity.admin.AdminPractitionerRepository.AccountRow;
import com.psymate.identity.admin.AdminPractitionerRepository.Filter;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerSuspended;
import com.psymate.shared.web.IpAddresses;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comptes des praticiens vus par l'administration (M1-14b, fiche 21) : liste, détail, suspension et
 * réactivation manuelles. Toute action est une ligne verification_decision (append-only) et une
 * ligne du journal d'administration (security_event). Aucun accès aux données patient.
 */
@Service
@Transactional
class AdminPractitionerService {

  static final int MIN_REASON = 20;
  static final int MAX_REASON = 4000;
  private static final int SUMMARY_REASON = 200;

  static final Set<String> STATUSES =
      Set.of(
          "email_pending",
          "docs_pending",
          "under_review",
          "approved",
          "needs_more",
          "rejected",
          "suspended",
          "closed");
  static final Set<String> ROLES = Set.of("practitioner", "admin");
  static final Set<String> PROFESSIONS = Set.of("psychologist", "psychiatrist", "coach");

  /** Statuts qu'une suspension manuelle peut frapper : tout compte encore actif ou en cours. */
  private static final Set<String> NOT_SUSPENDABLE = Set.of("suspended", "rejected", "closed");

  /** Statuts d'avant la suspension auxquels une réactivation peut rendre le compte. */
  private static final Set<String> RESTORABLE =
      Set.of("docs_pending", "under_review", "approved", "needs_more");

  private static final Pattern SEARCH = Pattern.compile("^.{1,100}$", Pattern.DOTALL);

  private final AdminPractitionerRepository repo;
  private final AdminVerificationRepository shared;
  private final AdminMailer mailer;
  private final AdminAsyncMail asyncMail;
  private final DomainEventPublisher events;
  private final JsonMapper json;
  private final Clock clock;

  AdminPractitionerService(
      AdminPractitionerRepository repo,
      AdminVerificationRepository shared,
      AdminMailer mailer,
      AdminAsyncMail asyncMail,
      DomainEventPublisher events,
      JsonMapper json,
      Clock clock) {
    this.repo = repo;
    this.shared = shared;
    this.mailer = mailer;
    this.asyncMail = asyncMail;
    this.events = events;
    this.json = json;
    this.clock = clock;
  }

  // ---- liste et détail

  @Transactional(readOnly = true)
  AccountListDto list(
      String q, String status, String role, String profession, String cursor, Integer limit) {
    String search = q == null || q.isBlank() ? null : q.trim();
    if (search != null && !SEARCH.matcher(search).matches()) {
      throw AdminException.fields(new FieldError("q", "TOO_LONG", "admin.error.toolong"));
    }
    if (status != null && !STATUSES.contains(status)) {
      throw AdminException.fields(
          new FieldError("status", "INVALID_STATUS", "admin.error.accounts.status"));
    }
    if (role != null && !ROLES.contains(role)) {
      throw AdminException.fields(
          new FieldError("role", "INVALID_ROLE", "admin.error.accounts.role"));
    }
    if (profession != null && !PROFESSIONS.contains(profession)) {
      throw AdminException.fields(
          new FieldError("profession", "INVALID_PROFESSION", "admin.error.accounts.profession"));
    }
    Position position = AdminCursors.decode(cursor);
    int max = AdminCursors.limit(limit);
    Filter filter = new Filter(search, status, role, profession);
    List<AccountRow> rows = repo.list(filter, position, max + 1);
    boolean more = rows.size() > max;
    List<AccountRow> page = more ? rows.subList(0, max) : rows;
    Instant now = clock.instant();
    String next = null;
    if (more) {
      AccountRow last = page.get(page.size() - 1);
      next = AdminCursors.encode(last.sortKey(), last.id());
    }
    return new AccountListDto(
        page.stream().map(r -> item(r, now)).toList(), next, repo.counts(filter));
  }

  @Transactional(readOnly = true)
  AccountDetailDto get(UUID id) {
    AccountRow r = repo.find(id).orElseThrow(AdminPractitionerService::notFound);
    AccountItemDto i = item(r, clock.instant());
    List<DecisionItemDto> decisions =
        shared.decisions(id).stream()
            .map(
                d ->
                    new DecisionItemDto(
                        d.id(),
                        d.kind(),
                        d.message(),
                        d.internalNote(),
                        checklist(d.checklistJson()),
                        d.decidedAt(),
                        d.adminId()))
            .toList();
    List<AppealSummaryDto> appeals =
        repo.appeals(id).stream()
            .map(
                a ->
                    new AppealSummaryDto(
                        a.id(),
                        a.decisionId(),
                        a.submittedAt(),
                        a.reviewedAt(),
                        AppealService.apiOutcome(a.outcome()),
                        a.message(),
                        a.responseMessage()))
            .toList();
    return new AccountDetailDto(
        i.id(),
        i.displayName(),
        i.email(),
        i.profession(),
        i.city(),
        i.role(),
        i.verification(),
        i.twoFactorEnabled(),
        i.createdAt(),
        i.lastLoginAt(),
        i.suspendedAt(),
        i.purgeAt(),
        decisions,
        appeals);
  }

  // ---- suspension

  SuspendDto suspend(CurrentPractitioner admin, UUID id, String rawReason, String ip) {
    String reason = reason(rawReason);
    // Cible administrateur : tous les administrateurs actifs sont verrouillés d'abord, dans un
    // ordre unique (par id), avant la cible. Deux suspensions croisées d'administrateurs se
    // sérialisent donc au lieu de s'interbloquer (deadlock Postgres → 500).
    boolean adminTarget =
        shared
            .findAccount(id)
            .map(a -> "admin".equals(a.role()))
            .orElseThrow(AdminPractitionerService::notFound);
    List<UUID> activeAdmins = adminTarget ? repo.lockActiveAdmins() : List.of();
    AdminVerificationRepository.AccountRow target =
        shared.lockAccount(id).orElseThrow(AdminPractitionerService::notFound);
    if (target.id().equals(admin.id())) {
      throw AdminException.of(HttpStatus.CONFLICT, "ADMIN_SELF_SUSPEND", "admin.error.selfsuspend");
    }
    if (NOT_SUSPENDABLE.contains(target.status())) {
      throw AdminException.of(
          HttpStatus.CONFLICT, "ADMIN_ACCOUNT_NOT_SUSPENDABLE", "admin.error.notsuspendable");
    }
    if ("admin".equals(target.role()) && activeAdmins.stream().noneMatch(a -> !a.equals(id))) {
      throw AdminException.of(HttpStatus.CONFLICT, "ADMIN_LAST_ADMIN", "admin.error.lastadmin");
    }

    Instant now = clock.instant();
    String cleanIp = IpAddresses.normalize(ip);
    UUID decisionId = UUID.randomUUID();
    repo.markSuspended(id, now);
    shared.insertDecision(
        decisionId,
        id,
        admin.id(),
        "suspended",
        null,
        reason,
        write(Map.of("previousStatus", target.status())),
        now);
    int revoked = shared.deleteSessions(id);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("summary", "Compte suspendu : " + truncate(reason, SUMMARY_REASON));
    details.put("reason", reason);
    details.put("sessionsRevoked", revoked);
    details.put("previousStatus", target.status());
    shared.insertAdminEvent(id, admin.id(), "admin.suspend", cleanIp, write(details), now);
    events.publish(new PractitionerSuspended(target.tenantId(), id, target.profession()));
    asyncMail.afterCommit(
        target.locale(),
        l -> mailer.sendSuspended(target.email(), l, target.displayName(), reason));
    return new SuspendDto(id, "suspended", now, decisionId);
  }

  // ---- réactivation

  ReactivateDto reactivate(CurrentPractitioner admin, UUID id, String rawReason, String ip) {
    String reason = reason(rawReason);
    AdminVerificationRepository.AccountRow target =
        shared.lockAccount(id).orElseThrow(AdminPractitionerService::notFound);
    if (!reactivable(target.status())) {
      throw AdminException.of(
          HttpStatus.CONFLICT, "ADMIN_ACCOUNT_NOT_REACTIVABLE", "admin.error.notreactivable");
    }
    Instant now = clock.instant();
    UUID decisionId = UUID.randomUUID();
    String newStatus = restore(admin, target, reason, ip, "manual", now, true);
    shared.insertDecision(
        decisionId,
        id,
        admin.id(),
        "reactivated",
        null,
        reason,
        write(Map.of("previousStatus", target.status(), "newStatus", newStatus)),
        now);
    return new ReactivateDto(id, newStatus, decisionId);
  }

  static boolean reactivable(String status) {
    return "suspended".equals(status) || "rejected".equals(status);
  }

  /**
   * Rend le compte à l'usage (réactivation manuelle ou recours accepté) : calcule le nouveau
   * statut, le pose, efface suspended_at et purge_after, journalise admin.reactivate et, si
   * demandé, prévient le praticien. Le compte est déjà verrouillé par l'appelant, qui écrit la
   * ligne verification_decision correspondante.
   *
   * @param source manual ou appeal, consigné dans le journal
   * @return le nouveau statut de vérification
   */
  String restore(
      CurrentPractitioner admin,
      AdminVerificationRepository.AccountRow target,
      String reason,
      String ip,
      String source,
      Instant now,
      boolean sendMail) {
    String newStatus = restoredStatus(target);
    repo.markReactivated(target.id(), newStatus, now);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put(
        "summary",
        ("appeal".equals(source) ? "Compte réactivé après recours : " : "Compte réactivé : ")
            + truncate(reason, SUMMARY_REASON));
    details.put("reason", reason);
    details.put("newStatus", newStatus);
    details.put("source", source);
    shared.insertAdminEvent(
        target.id(),
        admin.id(),
        "admin.reactivate",
        IpAddresses.normalize(ip),
        write(details),
        now);
    if (sendMail) {
      asyncMail.afterCommit(
          target.locale(), l -> mailer.sendReactivated(target.email(), l, target.displayName()));
    }
    return newStatus;
  }

  private String restoredStatus(AdminVerificationRepository.AccountRow target) {
    if ("rejected".equals(target.status())) {
      // Le dossier refusé doit être redéposé (fiche 21).
      return "docs_pending";
    }
    String previous = repo.previousStatusBeforeSuspension(target.id()).orElse(null);
    if (previous != null && RESTORABLE.contains(previous)) {
      return previous;
    }
    if (previous != null) {
      // email_pending ou valeur inconnue : le compte a de toute façon confirmé son e-mail depuis.
      return "docs_pending";
    }
    return target.verifiedAt() != null ? "approved" : "docs_pending";
  }

  // ---- utilitaires

  private static AccountItemDto item(AccountRow r, Instant now) {
    boolean overdue =
        "under_review".equals(r.status()) && AdminVerificationService.overdue(r.submittedAt(), now);
    return new AccountItemDto(
        r.id(),
        r.displayName(),
        r.email(),
        r.profession(),
        r.city(),
        r.role(),
        new VerificationDto(r.status(), since(r), overdue),
        r.twoFactorEnabled(),
        r.createdAt(),
        r.lastLoginAt(),
        r.suspendedAt(),
        r.purgeAt());
  }

  /** Date d'entrée dans le statut courant, à défaut la dernière mise à jour du compte. */
  private static Instant since(AccountRow r) {
    Instant s =
        switch (r.status()) {
          case "approved" -> r.verifiedAt();
          case "under_review" -> r.submittedAt();
          case "suspended", "rejected", "closed" -> r.suspendedAt();
          default -> null;
        };
    return s != null ? s : r.updatedAt();
  }

  /** Motif obligatoire : 20 à 4000 caractères après suppression des blancs de bord. */
  static String reason(String raw) {
    String reason = raw == null ? "" : raw.trim();
    if (reason.isEmpty()) {
      throw AdminException.fields(
          new FieldError("reason", "REQUIRED", "admin.error.reason.required"));
    }
    if (reason.length() < MIN_REASON) {
      throw AdminException.fields(
          new FieldError("reason", "TOO_SHORT", "admin.error.reason.short"));
    }
    if (reason.length() > MAX_REASON) {
      throw AdminException.fields(new FieldError("reason", "TOO_LONG", "admin.error.reason.long"));
    }
    return reason;
  }

  static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }

  private static AdminException notFound() {
    return AdminException.of(
        HttpStatus.NOT_FOUND, "ADMIN_PRACTITIONER_NOT_FOUND", "admin.error.practitioner");
  }

  private Map<String, Object> checklist(String jsonText) {
    if (jsonText == null) {
      return Map.of();
    }
    return json.readValue(jsonText, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  private String write(Object value) {
    return json.writeValueAsString(value);
  }
}
