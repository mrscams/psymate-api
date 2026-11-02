package com.psymate.patients;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.PatientDtos.CreatePatientRequest;
import com.psymate.patients.PatientDtos.FlagRequest;
import com.psymate.patients.PatientDtos.PatientDto;
import com.psymate.patients.PatientDtos.PatientPage;
import com.psymate.patients.PatientDtos.VersionDto;
import com.psymate.patients.api.PatientAccessException;
import com.psymate.patients.api.PatientArchived;
import com.psymate.patients.api.PatientCreated;
import com.psymate.patients.api.PatientFacade;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.patients.api.PatientRef;
import com.psymate.patients.api.PatientUpdated;
import com.psymate.patients.persistence.Patient;
import com.psymate.patients.persistence.PatientFlag;
import com.psymate.patients.persistence.PatientFlagRepository;
import com.psymate.patients.persistence.PatientJpaRepository;
import com.psymate.patients.persistence.PatientOwnership;
import com.psymate.patients.persistence.PatientTag;
import com.psymate.patients.persistence.PatientTagId;
import com.psymate.patients.persistence.PatientTagRepository;
import com.psymate.patients.persistence.PatientVersion;
import com.psymate.patients.persistence.PatientVersionRepository;
import com.psymate.shared.problem.ErrorCode;
import com.psymate.shared.problem.ProblemException;
import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.TagFacade;
import com.psymate.vocabulary.api.TagView;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

  /** Propriétaire et état d'un patient, lus sans exposer l'entité. */
  record Ownership(UUID tenantId, UUID ownerId, boolean archived) {}

  private final PatientJpaRepository patients;
  private final PatientVersionRepository versions;
  private final PatientFlagRepository patientFlags;
  private final PatientTagRepository patientTags;
  private final PatientAssembler assembler;
  private final DomainEventPublisher events;
  private final TagFacade tagFacade;
  private final EmotionCatalog emotions;
  private final TransactionTemplate tx;

  PatientService(
      PatientJpaRepository patients,
      PatientVersionRepository versions,
      PatientFlagRepository patientFlags,
      PatientTagRepository patientTags,
      PatientAssembler assembler,
      DomainEventPublisher events,
      TagFacade tagFacade,
      EmotionCatalog emotions,
      TransactionTemplate tx) {
    this.patients = patients;
    this.versions = versions;
    this.patientFlags = patientFlags;
    this.patientTags = patientTags;
    this.assembler = assembler;
    this.events = events;
    this.tagFacade = tagFacade;
    this.emotions = emotions;
    this.tx = tx;
  }

  // ---- façade et garde

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID patientId) {
    return patients.existsById(patientId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean ownedBy(UUID patientId, UUID practitionerId) {
    return patients
        .findOwnership(patientId)
        .map(PatientService::ownership)
        .filter(o -> o.ownerId().equals(practitionerId))
        .filter(
            o ->
                CurrentPractitioner.get()
                    .map(me -> me.tenantId().equals(o.tenantId()))
                    .orElse(true))
        .isPresent();
  }

  @Override
  @Transactional(readOnly = true)
  public List<PatientRef> ownedBy(UUID practitionerId) {
    return patients.findOwnedByOrdered(practitionerId).stream()
        .map(
            p ->
                new PatientRef(
                    p.getId(), p.getDisplayName(), p.getBirthDate(), p.getArchivedAt() != null))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> displayName(UUID patientId) {
    return patients.findById(patientId).map(Patient::getDisplayName);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LocalDate> birthDate(UUID patientId) {
    return patients.findById(patientId).map(Patient::getBirthDate);
  }

  @Override
  @Transactional
  public void touchClinicalRev(UUID patientId) {
    patients.touchClinicalRev(patientId);
  }

  @Override
  @Transactional(readOnly = true)
  public void requireOwned(UUID patientId) {
    owned(patientId);
  }

  private static void requireActive(Ownership o) {
    if (o.archived()) {
      throw PatientException.of(
          HttpStatus.CONFLICT, "PATIENT_ARCHIVED", "Patient archivé : restaurez-le d'abord.");
    }
  }

  private static Ownership ownership(Patient p) {
    return new Ownership(p.getTenantId(), p.getOwnerId(), p.getArchivedAt() != null);
  }

  private static Ownership ownership(PatientOwnership o) {
    return new Ownership(o.tenantId(), o.ownerId(), o.archivedAt() != null);
  }

  /** Propriété sans charger la fiche (projection) : 404 si absente, 403 si d'un autre praticien. */
  private Ownership owned(UUID patientId) {
    CurrentPractitioner me = me();
    Ownership o =
        patients
            .findOwnership(patientId)
            .map(PatientService::ownership)
            .orElseThrow(PatientAccessException::notFound);
    requireMine(me, o.tenantId(), o.ownerId());
    return o;
  }

  /**
   * Fiche du praticien courant chargée avec un verrou de ligne : toute écriture de l'entité (PATCH,
   * archivage, restauration) se sérialise avec les autres, sans mise à jour perdue.
   */
  private Patient lockedOwned(UUID patientId) {
    CurrentPractitioner me = me();
    Patient p = patients.findForUpdate(patientId).orElseThrow(PatientAccessException::notFound);
    requireMine(me, p.getTenantId(), p.getOwnerId());
    return p;
  }

  private static void requireMine(CurrentPractitioner me, UUID tenantId, UUID ownerId) {
    if (!ownerId.equals(me.id()) || !tenantId.equals(me.tenantId())) {
      throw PatientAccessException.forbidden();
    }
  }

  private PatientDto find(UUID id) {
    return patients.findById(id).map(p -> assembler.withRelations(List.of(p)).get(0)).orElseThrow();
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(
            () -> new ProblemException(401, ErrorCode.UNAUTHENTICATED, "Session requise."));
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
    List<Patient> rows =
        patients.list(
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
        assembler.withRelations(more ? rows.subList(0, limit) : rows),
        more ? encodeCursor(offset + limit) : null);
  }

  @Transactional(readOnly = true)
  PatientDto get(UUID id) {
    owned(id);
    return find(id);
  }

  @Transactional(readOnly = true)
  List<VersionDto> versions(UUID id) {
    owned(id);
    return versions.findByPatientIdOrderByVersion(id).stream()
        .map(
            v ->
                new VersionDto(
                    v.getVersion(),
                    new LinkedHashMap<>(v.getSnapshot()),
                    v.getReason(),
                    v.getAuthorId(),
                    v.getCreatedAt()))
        .toList();
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
    Set<String> kinds = new LinkedHashSet<>();
    for (FlagRequest fl : flags) {
      if (fl != null && !kinds.add(fl.kind())) {
        throw flagConflict();
      }
    }

    UUID id = UUID.randomUUID();
    OffsetDateTime now = Patient.now();
    List<UUID> tagIds = resolveTags(r.tags());
    patients.save(
        new Patient(
            id,
            me.tenantId(),
            me.id(),
            displayName,
            blankToNull(r.firstName()),
            blankToNull(r.lastName()),
            birth,
            blankToNull(r.pronoun()),
            blankToNull(r.phone()),
            blankToNull(r.email()),
            r.context(),
            r.followUp(),
            now));
    for (FlagRequest f : flags) {
      patientFlags.save(
          new PatientFlag(UUID.randomUUID(), id, f.kind(), blankToNull(f.reason()), me.id(), now));
    }
    patientTags.saveAll(
        tagIds.stream().map(t -> new PatientTag(new PatientTagId(id, t), now)).toList());
    events.publish(new PatientCreated(me.tenantId(), id, me.id()));
    return find(id);
  }

  @Transactional
  PatientDto patch(UUID id, Map<String, Object> body) {
    CurrentPractitioner me = me();
    // Verrou de ligne : deux PATCH concurrents se sérialisent (une version chacun, sans doublon).
    Patient cur = lockedOwned(id);
    requireActive(ownership(cur));
    List<FieldProblem> problems = new ArrayList<>();
    for (String key : body.keySet()) {
      if (!PATCHABLE.contains(key)) {
        problems.add(new FieldProblem(key, "Champ non modifiable ou inconnu."));
      }
    }
    String displayName = cur.getDisplayName();
    if (body.containsKey("displayName")) {
      displayName = requireName(asString(body.get("displayName")), problems);
    }
    LocalDate birth = cur.getBirthDate();
    if (body.containsKey("birthDate")) {
      Object v = body.get("birthDate");
      try {
        birth = v == null ? null : checkBirthDate(LocalDate.parse(v.toString()), problems);
      } catch (DateTimeParseException e) {
        problems.add(new FieldProblem("birthDate", "Date invalide (AAAA-MM-JJ)."));
      }
    }
    throwIfAny(problems);

    OffsetDateTime now = Patient.now();
    int version =
        cur.applyEdit(
            displayName,
            text(body, "firstName", cur.getFirstName()),
            text(body, "lastName", cur.getLastName()),
            birth,
            text(body, "pronoun", cur.getPronoun()),
            text(body, "phone", cur.getPhone()),
            text(body, "email", cur.getEmail()),
            merge(body, "context", cur.getContext()),
            merge(body, "followUp", cur.getFollowUp()),
            now);
    versions.save(
        new PatientVersion(
            UUID.randomUUID(),
            id,
            version,
            snapshot(cur),
            me.id(),
            asString(body.get("reason")),
            now));
    events.publish(new PatientUpdated(me.tenantId(), id, me.id()));
    return find(id);
  }

  @Transactional
  void archive(UUID id) {
    CurrentPractitioner me = me();
    Patient p = lockedOwned(id);
    if (p.isArchived()) {
      return;
    }
    p.archive(Patient.now());
    events.publish(new PatientArchived(me.tenantId(), id, me.id()));
  }

  @Transactional
  PatientDto restore(UUID id) {
    CurrentPractitioner me = me();
    Patient p = lockedOwned(id);
    if (p.isArchived()) {
      p.restore(Patient.now());
      events.publish(new PatientUpdated(me.tenantId(), id, me.id()));
    }
    return find(id);
  }

  /**
   * Pas de {@code @Transactional} ici : l'unicité du drapeau actif (ux_patient_flag_active) est
   * contrôlée au flush / commit, donc le 409 d'une course est attrapé APRÈS le commit (même schéma
   * que LinkService).
   */
  PatientDto addFlag(UUID id, FlagRequest f) {
    try {
      return tx.execute(status -> doAddFlag(id, f));
    } catch (DataIntegrityViolationException race) {
      throw flagConflict();
    }
  }

  private PatientDto doAddFlag(UUID id, FlagRequest f) {
    CurrentPractitioner me = me();
    requireActive(owned(id));
    List<FieldProblem> problems = new ArrayList<>();
    checkFlag(f, "flag", problems);
    throwIfAny(problems);
    if (patientFlags.existsByPatientIdAndKindAndClearedAtIsNull(id, f.kind())) {
      throw flagConflict();
    }
    patientFlags.save(
        new PatientFlag(
            UUID.randomUUID(), id, f.kind(), blankToNull(f.reason()), me.id(), Patient.now()));
    events.publish(new PatientUpdated(me.tenantId(), id, me.id()));
    return find(id);
  }

  @Transactional
  PatientDto clearFlag(UUID id, String kind) {
    CurrentPractitioner me = me();
    requireActive(owned(id));
    if (!FLAG_KINDS.contains(kind)) {
      throw PatientException.invalid("kind", "Drapeau inconnu : watch, risk ou shared_care.");
    }
    if (patientFlags.clearActive(id, kind, me.id(), Patient.now()) == 0) {
      throw PatientException.of(
          HttpStatus.NOT_FOUND, "FLAG_NOT_ACTIVE", "Ce drapeau n'est pas actif.");
    }
    events.publish(new PatientUpdated(me.tenantId(), id, me.id()));
    return find(id);
  }

  @Transactional
  PatientDto replaceTags(UUID id, List<String> labels) {
    CurrentPractitioner me = me();
    requireActive(owned(id));
    List<UUID> wanted = resolveTags(labels);
    // Même sémantique d'ensemble que l'ancien SQL : suppression des absents, insertion idempotente
    // (on conflict do nothing) des manquants, sans lecture préalable ni course entre deux PUT.
    if (wanted.isEmpty()) {
      patientTags.deleteAllOf(id);
    } else {
      patientTags.deleteOthers(id, wanted);
    }
    for (UUID tagId : wanted) {
      patientTags.insertIgnoringDuplicate(id, tagId);
    }
    events.publish(new PatientUpdated(me.tenantId(), id, me.id()));
    return find(id);
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

  private static Map<String, Object> snapshot(Patient p) {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("displayName", p.getDisplayName());
    s.put("firstName", p.getFirstName());
    s.put("lastName", p.getLastName());
    s.put("birthDate", p.getBirthDate() == null ? null : p.getBirthDate().toString());
    s.put("pronoun", p.getPronoun());
    s.put("phone", p.getPhone());
    s.put("email", p.getEmail());
    s.put("context", new LinkedHashMap<>(p.getContext()));
    s.put("followUp", new LinkedHashMap<>(p.getFollowUp()));
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
