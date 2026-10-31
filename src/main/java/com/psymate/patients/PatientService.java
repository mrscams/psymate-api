package com.psymate.patients;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.PatientDtos.CreatePatientRequest;
import com.psymate.patients.PatientDtos.FlagRequest;
import com.psymate.patients.PatientDtos.PatientDto;
import com.psymate.patients.PatientDtos.PatientPage;
import com.psymate.patients.PatientDtos.VersionDto;
import com.psymate.patients.PatientRepository.Ownership;
import com.psymate.patients.api.PatientArchived;
import com.psymate.patients.api.PatientCreated;
import com.psymate.patients.api.PatientFacade;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.patients.api.PatientUpdated;
import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.TagFacade;
import com.psymate.vocabulary.api.TagView;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class PatientService implements PatientFacade, PatientOwnershipGuard {

  static final Set<String> FLAG_KINDS = Set.of("watch", "risk", "shared_care");
  private static final Set<String> PATCHABLE =
      Set.of(
          "displayName",
          "firstName",
          "lastName",
          "birthDate",
          "pronoun",
          "phone",
          "email",
          "context",
          "followUp",
          "reason");
  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 200;

  private final PatientRepository repo;
  private final DomainEventPublisher events;
  private final TagFacade tagFacade;
  private final EmotionCatalog emotions;

  PatientService(
      PatientRepository repo,
      DomainEventPublisher events,
      TagFacade tagFacade,
      EmotionCatalog emotions) {
    this.repo = repo;
    this.events = events;
    this.tagFacade = tagFacade;
    this.emotions = emotions;
  }

  // ---- façade et garde

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID patientId) {
    return repo.ownership(patientId).isPresent();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean ownedBy(UUID patientId, UUID practitionerId) {
    return repo.ownership(patientId).filter(o -> o.ownerId().equals(practitionerId)).isPresent();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> displayName(UUID patientId) {
    return repo.displayName(patientId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LocalDate> birthDate(UUID patientId) {
    return repo.birthDate(patientId);
  }

  @Override
  @Transactional(readOnly = true)
  public void requireOwned(UUID patientId) {
    owned(patientId);
  }

  private Ownership owned(UUID patientId) {
    CurrentPractitioner me = me();
    Ownership o =
        repo.ownership(patientId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient introuvable."));
    if (!o.ownerId().equals(me.id()) || !o.tenantId().equals(me.tenantId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce patient n'est pas le vôtre.");
    }
    return o;
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session requise."));
  }

  // ---- lecture

  @Transactional(readOnly = true)
  PatientPage list(
      String q, boolean archived, String tag, String sort, String cursor, Integer limitParam) {
    CurrentPractitioner me = me();
    String orderBy =
        switch (sort == null || sort.isBlank() ? "name" : sort) {
          case "name" -> "f_unaccent(lower(display_name)), id";
          case "created" -> "created_at desc, id";
          case "updated" -> "updated_at desc, id";
          default ->
              throw PatientException.invalid("sort", "Tri inconnu : name, created ou updated.");
        };
    int limit = limitParam == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limitParam, MAX_LIMIT));
    int offset = decodeCursor(cursor);
    UUID tagId = null;
    if (tag != null && !tag.isBlank()) {
      Optional<TagView> resolved = tagFacade.resolve(tag);
      if (resolved.isEmpty()) {
        return new PatientPage(List.of(), null);
      }
      tagId = resolved.get().id();
    }
    List<PatientDto> rows =
        repo.list(
            me.tenantId(),
            me.id(),
            NameFilter.pattern(q),
            archived,
            tagId,
            orderBy,
            limit + 1,
            offset);
    boolean more = rows.size() > limit;
    return new PatientPage(
        more ? rows.subList(0, limit) : rows, more ? encodeCursor(offset + limit) : null);
  }

  @Transactional(readOnly = true)
  PatientDto get(UUID id) {
    owned(id);
    return repo.find(id).orElseThrow();
  }

  @Transactional(readOnly = true)
  List<VersionDto> versions(UUID id) {
    owned(id);
    return repo.versions(id);
  }

  // ---- écriture

  @Transactional
  PatientDto create(CreatePatientRequest r) {
    CurrentPractitioner me = me();
    List<FieldProblem> problems = new ArrayList<>();
    String displayName = requireName(r.displayName(), problems);
    LocalDate birth = checkBirthDate(r.birthDate(), problems);
    List<FlagRequest> flags = r.flags() == null ? List.of() : r.flags();
    for (int i = 0; i < flags.size(); i++) {
      checkFlag(flags.get(i), "flags[" + i + "]", problems);
    }
    throwIfAny(problems);

    UUID id = UUID.randomUUID();
    PatientDto draft =
        new PatientDto(
            id,
            displayName,
            blankToNull(r.firstName()),
            blankToNull(r.lastName()),
            birth,
            blankToNull(r.pronoun()),
            blankToNull(r.phone()),
            blankToNull(r.email()),
            r.context() == null ? Map.of() : r.context(),
            r.followUp() == null ? Map.of() : r.followUp(),
            List.of(),
            List.of(),
            1,
            null,
            null,
            null);
    List<UUID> tagIds = resolveTags(r.tags());
    repo.insert(id, me.tenantId(), me.id(), draft);
    Set<String> seen = new LinkedHashSet<>();
    for (FlagRequest f : flags) {
      if (!seen.add(f.kind())) {
        throw flagConflict();
      }
      repo.insertFlag(id, f.kind(), blankToNull(f.reason()), me.id());
    }
    if (!tagIds.isEmpty()) {
      repo.replaceTags(id, tagIds);
    }
    events.publish(new PatientCreated(me.tenantId(), id, me.id()));
    return repo.find(id).orElseThrow();
  }

  @Transactional
  PatientDto patch(UUID id, Map<String, Object> body) {
    CurrentPractitioner me = me();
    Ownership o = owned(id);
    if (o.archived()) {
      throw PatientException.of(
          HttpStatus.CONFLICT, "PATIENT_ARCHIVED", "Patient archivé : restaurez-le d'abord.");
    }
    List<FieldProblem> problems = new ArrayList<>();
    for (String key : body.keySet()) {
      if (!PATCHABLE.contains(key)) {
        problems.add(new FieldProblem(key, "Champ non modifiable ou inconnu."));
      }
    }
    PatientDto cur = repo.find(id).orElseThrow();
    String displayName = cur.displayName();
    if (body.containsKey("displayName")) {
      displayName = requireName(asString(body.get("displayName")), problems);
    }
    LocalDate birth = cur.birthDate();
    if (body.containsKey("birthDate")) {
      Object v = body.get("birthDate");
      try {
        birth = v == null ? null : checkBirthDate(LocalDate.parse(v.toString()), problems);
      } catch (DateTimeParseException e) {
        problems.add(new FieldProblem("birthDate", "Date invalide (AAAA-MM-JJ)."));
      }
    }
    throwIfAny(problems);

    PatientDto next =
        new PatientDto(
            id,
            displayName,
            text(body, "firstName", cur.firstName()),
            text(body, "lastName", cur.lastName()),
            birth,
            text(body, "pronoun", cur.pronoun()),
            text(body, "phone", cur.phone()),
            text(body, "email", cur.email()),
            merge(body, "context", cur.context()),
            merge(body, "followUp", cur.followUp()),
            cur.flags(),
            cur.tags(),
            cur.currentVersion(),
            null,
            cur.createdAt(),
            cur.updatedAt());
    int version = repo.update(id, next);
    repo.insertVersion(id, version, snapshot(next), me.id(), asString(body.get("reason")));
    events.publish(new PatientUpdated(me.tenantId(), id, me.id()));
    return repo.find(id).orElseThrow();
  }

  @Transactional
  void archive(UUID id) {
    CurrentPractitioner me = me();
    Ownership o = owned(id);
    if (o.archived()) {
      return;
    }
    repo.setArchived(id, true);
    events.publish(new PatientArchived(me.tenantId(), id, me.id()));
  }

  @Transactional
  PatientDto restore(UUID id) {
    CurrentPractitioner me = me();
    Ownership o = owned(id);
    if (o.archived()) {
      repo.setArchived(id, false);
      events.publish(new PatientUpdated(me.tenantId(), id, me.id()));
    }
    return repo.find(id).orElseThrow();
  }

  @Transactional
  PatientDto addFlag(UUID id, FlagRequest f) {
    CurrentPractitioner me = me();
    owned(id);
    List<FieldProblem> problems = new ArrayList<>();
    checkFlag(f, "flag", problems);
    throwIfAny(problems);
    if (repo.activeFlagExists(id, f.kind())) {
      throw flagConflict();
    }
    try {
      repo.insertFlag(id, f.kind(), blankToNull(f.reason()), me.id());
    } catch (DuplicateKeyException e) {
      throw flagConflict();
    }
    events.publish(new PatientUpdated(me.tenantId(), id, me.id()));
    return repo.find(id).orElseThrow();
  }

  @Transactional
  PatientDto clearFlag(UUID id, String kind) {
    CurrentPractitioner me = me();
    owned(id);
    if (!FLAG_KINDS.contains(kind)) {
      throw PatientException.invalid("kind", "Drapeau inconnu : watch, risk ou shared_care.");
    }
    if (repo.clearFlag(id, kind, me.id()) == 0) {
      throw PatientException.of(
          HttpStatus.NOT_FOUND, "FLAG_NOT_ACTIVE", "Ce drapeau n'est pas actif.");
    }
    events.publish(new PatientUpdated(me.tenantId(), id, me.id()));
    return repo.find(id).orElseThrow();
  }

  @Transactional
  PatientDto replaceTags(UUID id, List<String> labels) {
    CurrentPractitioner me = me();
    owned(id);
    repo.replaceTags(id, resolveTags(labels));
    events.publish(new PatientUpdated(me.tenantId(), id, me.id()));
    return repo.find(id).orElseThrow();
  }

  // ---- règles

  /** Libellés résolus en tags actifs du praticien ; une émotion est refusée (TAG_IS_EMOTION). */
  private List<UUID> resolveTags(List<String> labels) {
    if (labels == null) {
      return List.of();
    }
    Set<UUID> ids = new LinkedHashSet<>();
    for (int i = 0; i < labels.size(); i++) {
      String label = labels.get(i);
      String field = "tags[" + i + "]";
      if (label == null || label.replace("#", "").isBlank()) {
        throw PatientException.invalid(field, "Le libellé du tag est obligatoire.");
      }
      var emotion = emotions.findActiveByLabel(label);
      if (emotion.isPresent()) {
        String fr = emotion.get().labelFr();
        throw new PatientException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TAG_IS_EMOTION",
            "Ce libellé est déjà une émotion : utilisez « " + fr + " ».",
            List.of(
                new PatientException.FieldError(
                    field, "Ce libellé correspond à l'émotion « " + fr + " ».")));
      }
      TagView tag =
          tagFacade
              .resolve(label)
              .orElseThrow(
                  () ->
                      new PatientException(
                          HttpStatus.UNPROCESSABLE_ENTITY,
                          "TAG_UNKNOWN",
                          "Tag inconnu : créez-le d'abord (POST /api/tags).",
                          List.of(new PatientException.FieldError(field, "Ce tag n'existe pas."))));
      ids.add(tag.id());
    }
    return new ArrayList<>(ids);
  }

  private record FieldProblem(String field, String message) {}

  private static void throwIfAny(List<FieldProblem> problems) {
    if (!problems.isEmpty()) {
      throw new PatientException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "VALIDATION_FAILED",
          problems.get(0).message(),
          problems.stream()
              .map(p -> new PatientException.FieldError(p.field(), p.message()))
              .toList());
    }
  }

  private static String requireName(String name, List<FieldProblem> problems) {
    if (name == null || name.isBlank()) {
      problems.add(new FieldProblem("displayName", "Le nom affiché est obligatoire."));
      return null;
    }
    return name.trim();
  }

  private static LocalDate checkBirthDate(LocalDate d, List<FieldProblem> problems) {
    if (d != null && (d.isAfter(LocalDate.now()) || d.isBefore(LocalDate.now().minusYears(120)))) {
      problems.add(
          new FieldProblem(
              "birthDate", "La date de naissance doit être passée et de moins de 120 ans."));
    }
    return d;
  }

  private static void checkFlag(FlagRequest f, String field, List<FieldProblem> problems) {
    if (f == null || f.kind() == null || !FLAG_KINDS.contains(f.kind())) {
      problems.add(
          new FieldProblem(field + ".kind", "Drapeau inconnu : watch, risk ou shared_care."));
    } else if ("watch".equals(f.kind()) && (f.reason() == null || f.reason().isBlank())) {
      problems.add(
          new FieldProblem(field + ".reason", "Une raison est obligatoire pour « à surveiller »."));
    }
  }

  private static PatientException flagConflict() {
    return PatientException.of(
        HttpStatus.CONFLICT, "FLAG_ALREADY_ACTIVE", "Ce drapeau est déjà actif.");
  }

  private static Map<String, Object> snapshot(PatientDto p) {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("displayName", p.displayName());
    s.put("firstName", p.firstName());
    s.put("lastName", p.lastName());
    s.put("birthDate", p.birthDate() == null ? null : p.birthDate().toString());
    s.put("pronoun", p.pronoun());
    s.put("phone", p.phone());
    s.put("email", p.email());
    s.put("context", p.context());
    s.put("followUp", p.followUp());
    return s;
  }

  private static String asString(Object o) {
    return o == null ? null : o.toString();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String text(Map<String, Object> body, String key, String current) {
    return body.containsKey(key) ? blankToNull(asString(body.get(key))) : current;
  }

  /** Fusion superficielle ; une valeur null retire la clé. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> merge(
      Map<String, Object> body, String key, Map<String, Object> current) {
    if (!body.containsKey(key)) {
      return current;
    }
    Object v = body.get(key);
    if (v == null) {
      return Map.of();
    }
    if (!(v instanceof Map<?, ?> patch)) {
      throw PatientException.invalid(key, "Un objet est attendu.");
    }
    Map<String, Object> out = new LinkedHashMap<>(current);
    ((Map<String, Object>) patch)
        .forEach(
            (k, val) -> {
              if (val == null) {
                out.remove(k);
              } else {
                out.put(k, val);
              }
            });
    return out;
  }

  private static String encodeCursor(int offset) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(("o:" + offset).getBytes(StandardCharsets.UTF_8));
  }

  private static int decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return 0;
    }
    try {
      String s = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int n = Integer.parseInt(s.substring(2));
      if (!s.startsWith("o:") || n < 0) {
        throw new IllegalArgumentException();
      }
      return n;
    } catch (IllegalArgumentException | StringIndexOutOfBoundsException e) {
      throw PatientException.invalid("cursor", "Curseur invalide.");
    }
  }
}
