package com.psymate.identity.admin.service;

import com.psymate.identity.admin.dto.AppealCreatedDto;
import com.psymate.identity.admin.dto.AppealDecisionRequest;
import com.psymate.identity.admin.dto.AppealItemDto;
import com.psymate.identity.admin.dto.AppealListDto;
import com.psymate.identity.admin.model.AdminCursors;
import com.psymate.identity.admin.model.AdminCursors.Position;
import com.psymate.identity.admin.model.AdminException;
import com.psymate.identity.admin.model.AdminException.FieldError;
import com.psymate.identity.admin.repository.AdminVerificationQueries;
import com.psymate.identity.admin.repository.AppealQueries;
import com.psymate.identity.admin.repository.AppealQueries.AppealRow;
import com.psymate.identity.admin.repository.AppealQueries.DecisionRef;
import com.psymate.identity.admin.repository.AppealQueries.ExistingAppeal;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.SuspendedAccount;
import com.psymate.identity.persistence.UniqueViolations;
import com.psymate.shared.web.IpAddresses;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recours du compte refusé ou suspendu (fiche 27, M1-14b, G Q28) : un seul par décision, examiné
 * par un administrateur autre que l'auteur de la décision. Le recours référence toujours la
 * dernière décision rejected ou suspended (practitioner_appeal.decision_id est obligatoire).
 */
@Service
@Transactional
public class AppealService {

  static final int MIN_MESSAGE = 20;
  static final int MAX_MESSAGE = 4000;
  static final Set<String> STATUSES = Set.of("open", "decided", "all");
  static final Set<String> OUTCOMES = Set.of("accepted", "dismissed");

  private final AppealQueries repo;
  private final AdminVerificationQueries shared;
  private final AdminPractitionerService accounts;
  private final AdminMailer mailer;
  private final AdminAsyncMail asyncMail;
  private final JsonMapper json;
  private final Clock clock;
  private final TransactionTemplate tx;

  AppealService(
      AppealQueries repo,
      AdminVerificationQueries shared,
      AdminPractitionerService accounts,
      AdminMailer mailer,
      AdminAsyncMail asyncMail,
      JsonMapper json,
      Clock clock,
      PlatformTransactionManager txManager) {
    this.tx = new TransactionTemplate(txManager);
    this.repo = repo;
    this.shared = shared;
    this.accounts = accounts;
    this.mailer = mailer;
    this.asyncMail = asyncMail;
    this.json = json;
    this.clock = clock;
  }

  /** Message du praticien : 20 à 4000 caractères après suppression des blancs de bord. */
  public static String message(String raw) {
    String message = raw == null ? "" : raw.trim();
    if (message.isEmpty()) {
      throw AdminException.fields(new FieldError("message", "REQUIRED", "admin.error.required"));
    }
    if (message.length() < MIN_MESSAGE) {
      throw AdminException.fields(
          new FieldError("message", "TOO_SHORT", "admin.error.appeal.message.short"));
    }
    if (message.length() > MAX_MESSAGE) {
      throw AdminException.fields(
          new FieldError("message", "TOO_LONG", "admin.error.appeal.message.long"));
    }
    return message;
  }

  /** En base 'rejected' signifie que l'administration a écarté le recours : dismissed en API. */
  static String apiOutcome(String dbOutcome) {
    return "rejected".equals(dbOutcome) ? "dismissed" : dbOutcome;
  }

  // ---- dépôt par le praticien

  /**
   * Dépose le recours d'un compte déjà identifié par AppealAuthenticator. La transaction est celle
   * du TransactionTemplate et non celle de la classe : la violation de ux_appeal_decision (un seul
   * recours par décision) est levée par Hibernate au flush, donc au commit, et doit être traduite
   * en 409 après lui.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public AppealCreatedDto submit(SuspendedAccount who, String message) {
    try {
      return tx.execute(status -> doSubmit(who, message));
    } catch (DataIntegrityViolationException e) {
      if (!UniqueViolations.isUniqueViolation(e)) {
        throw e;
      }
      throw AdminException.of(
          HttpStatus.CONFLICT, "APPEAL_ALREADY_OPEN", "admin.error.appeal.open");
    }
  }

  private AppealCreatedDto doSubmit(SuspendedAccount who, String message) {
    // Verrou du praticien : sérialise deux dépôts, et un dépôt avec une décision d'administrateur.
    AdminVerificationQueries.AccountRow account =
        shared
            .lockAccount(who.id())
            .orElseThrow(
                () ->
                    AdminException.of(
                        HttpStatus.CONFLICT,
                        "APPEAL_NOT_ALLOWED",
                        "admin.error.appeal.notallowed"));
    if (!"rejected".equals(account.status()) && !"suspended".equals(account.status())) {
      throw AdminException.of(
          HttpStatus.CONFLICT, "APPEAL_NOT_ALLOWED", "admin.error.appeal.notallowed");
    }
    DecisionRef decision =
        repo.latestContestableDecision(who.id())
            .orElseThrow(
                () ->
                    AdminException.of(
                        HttpStatus.CONFLICT,
                        "APPEAL_NOT_ALLOWED",
                        "admin.error.appeal.notallowed"));
    // Un seul recours ouvert par praticien, quelle que soit la décision : un recours resté en
    // attente après une réactivation manuelle puis une nouvelle suspension doit d'abord être
    // examiné (rejeté) par l'administration.
    if (repo.hasOpenAppeal(who.id())) {
      throw AdminException.of(
          HttpStatus.CONFLICT, "APPEAL_ALREADY_OPEN", "admin.error.appeal.open");
    }
    ExistingAppeal existing = repo.findByDecision(decision.id()).orElse(null);
    if (existing != null) {
      throw existing.reviewed()
          ? AdminException.of(HttpStatus.CONFLICT, "APPEAL_ALREADY_USED", "admin.error.appeal.used")
          : AdminException.of(
              HttpStatus.CONFLICT, "APPEAL_ALREADY_OPEN", "admin.error.appeal.open");
    }
    Instant now = clock.instant();
    UUID id = UUID.randomUUID();
    repo.insert(id, who.id(), decision.id(), message, now);
    // actor_id NULL : c'est le praticien lui-même qui agit.
    shared.insertAdminEvent(
        who.id(),
        null,
        "appeal.submitted",
        null,
        write(Map.of("summary", "Recours déposé", "appealId", id, "decisionId", decision.id())),
        now);
    return new AppealCreatedDto(id, now, "open");
  }

  // ---- file des administrateurs

  @Transactional(readOnly = true)
  public AppealListDto list(String status, String cursor, Integer limit) {
    String st = status == null || status.isBlank() ? "open" : status;
    if (!STATUSES.contains(st)) {
      throw AdminException.fields(
          new FieldError("status", "INVALID_STATUS", "admin.error.appeal.status"));
    }
    Position position = AdminCursors.decode(cursor);
    int max = AdminCursors.limit(limit);
    List<AppealRow> rows = repo.page(st, position, max + 1);
    boolean more = rows.size() > max;
    List<AppealRow> page = more ? rows.subList(0, max) : rows;
    String next = null;
    if (more) {
      AppealRow last = page.get(page.size() - 1);
      next = AdminCursors.encode(last.sortKey(), last.id());
    }
    return new AppealListDto(page.stream().map(AppealService::item).toList(), next);
  }

  /**
   * Décide d'un recours. accepted : le compte est réactivé comme par /reactivate (statut restauré,
   * admin.reactivate au journal) mais la seule ligne verification_decision est appeal_accepted, et
   * un seul e-mail part, celui du recours accepté. dismissed : le compte est inchangé.
   */
  public AppealItemDto decide(
      CurrentPractitioner admin, UUID appealId, AppealDecisionRequest body, String ip) {
    String outcome = body.outcome();
    if (outcome == null || !OUTCOMES.contains(outcome)) {
      throw AdminException.fields(
          new FieldError("outcome", "INVALID_OUTCOME", "admin.error.appeal.outcome"));
    }
    String message =
        body.messageToPractitioner() == null ? "" : body.messageToPractitioner().trim();
    if (message.isEmpty()) {
      throw AdminException.fields(
          new FieldError("messageToPractitioner", "REQUIRED", "admin.error.required"));
    }
    if (message.length() > AdminVerificationService.MAX_TEXT) {
      throw AdminException.fields(
          new FieldError("messageToPractitioner", "TOO_LONG", "admin.error.toolong"));
    }
    AppealRow found = repo.find(appealId).orElseThrow(AppealService::notFound);
    // Verrou du praticien d'abord (même ordre que le dépôt), puis relecture du recours.
    AdminVerificationQueries.AccountRow target =
        shared.lockAccount(found.practitionerId()).orElseThrow(AppealService::notFound);
    AppealRow appeal = repo.find(appealId).orElseThrow(AppealService::notFound);
    if (appeal.reviewedAt() != null) {
      throw AdminException.of(
          HttpStatus.CONFLICT, "APPEAL_ALREADY_DECIDED", "admin.error.appeal.decided");
    }
    if (admin.id().equals(appeal.decisionAdminId())) {
      throw AdminException.of(
          HttpStatus.CONFLICT, "APPEAL_SELF_REVIEW", "admin.error.appeal.selfreview");
    }
    boolean accepted = "accepted".equals(outcome);
    if (accepted && !AdminPractitionerService.reactivable(target.status())) {
      throw AdminException.of(
          HttpStatus.CONFLICT,
          "APPEAL_ACCOUNT_NOT_REACTIVABLE",
          "admin.error.appeal.notreactivable");
    }

    Instant now = clock.instant();
    shared.insertDecision(
        UUID.randomUUID(),
        target.id(),
        admin.id(),
        accepted ? "appeal_accepted" : "appeal_rejected",
        null,
        message,
        write(Map.of("decisionId", appeal.decisionId())),
        appealId,
        now);
    repo.decide(appealId, admin.id(), accepted ? "accepted" : "rejected", message, now);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put(
        "summary",
        (accepted ? "Recours accepté : " : "Recours rejeté : ")
            + AdminPractitionerService.truncate(message, 200));
    details.put("appealId", appealId);
    details.put("outcome", outcome);
    shared.insertAdminEvent(
        target.id(), admin.id(), "admin.appeal", IpAddresses.normalize(ip), write(details), now);
    if (accepted) {
      accounts.restore(admin, target, message, ip, "appeal", now, false);
      asyncMail.afterCommit(
          target.locale(),
          l -> mailer.sendAppealAccepted(target.email(), l, target.displayName(), message));
    } else {
      asyncMail.afterCommit(
          target.locale(),
          l -> mailer.sendAppealDismissed(target.email(), l, target.displayName(), message));
    }
    return item(repo.find(appealId).orElseThrow(AppealService::notFound));
  }

  // ---- utilitaires

  private static AppealItemDto item(AppealRow r) {
    return new AppealItemDto(
        r.id(),
        r.practitionerId(),
        r.practitionerName(),
        r.decisionId(),
        r.decisionKind(),
        r.decisionAdminId(),
        r.message(),
        r.submittedAt(),
        r.reviewedAt(),
        r.reviewedBy(),
        apiOutcome(r.outcome()),
        r.responseMessage());
  }

  private static AdminException notFound() {
    return AdminException.of(
        HttpStatus.NOT_FOUND, "APPEAL_NOT_FOUND", "admin.error.appeal.notfound");
  }

  private String write(Object value) {
    return json.writeValueAsString(value);
  }
}
