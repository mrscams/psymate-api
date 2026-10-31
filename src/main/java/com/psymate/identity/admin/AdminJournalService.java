package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminAccountDtos.ActorDto;
import com.psymate.identity.admin.AdminAccountDtos.JournalDto;
import com.psymate.identity.admin.AdminAccountDtos.JournalItemDto;
import com.psymate.identity.admin.AdminCursors.Position;
import com.psymate.identity.admin.AdminException.FieldError;
import com.psymate.identity.admin.AdminJournalRepository.Filter;
import com.psymate.identity.admin.AdminJournalRepository.JournalRow;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Journal d'administration en lecture seule (fiche 22, M1-14b, G Q29) : décisions et événements de
 * sécurité de la vue admin_journal_v. Aucune écriture, aucune adresse IP, aucune donnée patient.
 */
@Service
@Transactional(readOnly = true)
class AdminJournalService {

  private static final Pattern KIND = Pattern.compile("^[a-z][a-z0-9_.]{0,63}$");

  private final AdminJournalRepository repo;
  private final JsonMapper json;

  AdminJournalService(AdminJournalRepository repo, JsonMapper json) {
    this.repo = repo;
    this.json = json;
  }

  JournalDto list(
      String kind,
      String practitionerId,
      String actorId,
      String from,
      String to,
      String cursor,
      Integer limit) {
    String k = kind == null || kind.isBlank() ? null : kind.trim();
    if (k != null && !KIND.matcher(k).matches()) {
      throw AdminException.fields(
          new FieldError("kind", "INVALID_KIND", "admin.error.journal.kind"));
    }
    UUID practitioner = uuid("practitionerId", practitionerId);
    UUID actor = uuid("actorId", actorId);
    Instant fromAt = date("from", from, false);
    Instant toAt = date("to", to, true);
    Position position = AdminCursors.decode(cursor);
    int max = AdminCursors.limit(limit);
    List<JournalRow> rows =
        repo.page(new Filter(k, practitioner, actor, fromAt, toAt), position, max + 1);
    boolean more = rows.size() > max;
    List<JournalRow> page = more ? rows.subList(0, max) : rows;
    String next = null;
    if (more) {
      JournalRow last = page.get(page.size() - 1);
      next = AdminCursors.encode(last.at(), last.id());
    }
    return new JournalDto(page.stream().map(this::item).toList(), next);
  }

  private JournalItemDto item(JournalRow r) {
    ActorDto actor = r.actorId() == null ? null : new ActorDto(r.actorId(), r.actorName());
    // admin : un administrateur a agi ; practitioner : le compte lui-même ; system : ni l'un ni
    // l'autre.
    String actorType =
        r.actorId() != null ? "admin" : r.practitionerId() != null ? "practitioner" : "system";
    return new JournalItemDto(
        r.id(),
        r.at(),
        r.kind(),
        r.source(),
        actor,
        actorType,
        r.practitionerId(),
        r.practitionerName(),
        r.summary(),
        details(r.detailsJson()));
  }

  private Map<String, Object> details(String jsonText) {
    if (jsonText == null) {
      return Map.of();
    }
    return json.readValue(jsonText, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  private static UUID uuid(String field, String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      throw AdminException.fields(new FieldError(field, "INVALID_ID", "admin.error.journal.uuid"));
    }
  }

  /** Date-heure ISO 8601 avec fuseau, ou jour AAAA-MM-JJ (début, ou fin de journée pour to). */
  private static Instant date(String field, String raw, boolean endOfDay) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String s = raw.trim();
    try {
      if (s.length() == 10) {
        LocalDate day = LocalDate.parse(s);
        return endOfDay
            ? day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1)
            : day.atStartOfDay().toInstant(ZoneOffset.UTC);
      }
      return OffsetDateTime.parse(s).toInstant();
    } catch (DateTimeException e) {
      throw AdminException.fields(
          new FieldError(field, "INVALID_DATE", "admin.error.journal.date"));
    }
  }
}
