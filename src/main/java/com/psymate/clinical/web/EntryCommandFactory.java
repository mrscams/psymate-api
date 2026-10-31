package com.psymate.clinical.web;

import com.psymate.clinical.application.CorrectEntryCommand;
import com.psymate.clinical.application.CreateEntryCommand;
import com.psymate.clinical.application.EntryDetails;
import com.psymate.clinical.application.StartSessionCommand;
import com.psymate.clinical.domain.EntryChecks;
import com.psymate.clinical.domain.EntryContent;
import com.psymate.clinical.domain.EntryStatus;
import com.psymate.clinical.domain.EntryType;
import com.psymate.clinical.domain.FeelingDetail;
import com.psymate.clinical.domain.MedicationDetail;
import com.psymate.clinical.domain.SessionDetail;
import com.psymate.clinical.domain.TimeSpan;
import com.psymate.clinical.domain.TimeSpan.Precision;
import com.psymate.clinical.domain.TimeSpec;
import com.psymate.clinical.domain.Visibility;
import com.psymate.clinical.web.EntryDtos.CreateEntryRequest;
import com.psymate.clinical.web.EntryDtos.EntryFeelingRequest;
import com.psymate.clinical.web.EntryDtos.EntryMedicationRequest;
import com.psymate.clinical.web.EntryDtos.EntryOccurredRequest;
import com.psymate.clinical.web.EntryDtos.EntrySessionRequest;
import com.psymate.clinical.web.EntryDtos.PatchEntryRequest;
import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.TagFacade;
import com.psymate.vocabulary.api.TagView;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Traduit un corps de requête en commande applicative et vérifie tout ce qui peut l'être AVANT
 * d'écrire, en cumulant les erreurs : une entrée {field, code} par champ fautif (format, champs
 * minimaux du type par les groupes Jakarta du domaine, émotion et tags connus).
 */
@Component
class EntryCommandFactory {

  /** Instant factice : sert seulement à satisfaire @NotNull sur le temps pendant la validation. */
  private static final TimeSpan PLACEHOLDER_TIME =
      new TimeSpan(Instant.EPOCH, null, Precision.EXACT, null);

  /**
   * Création analysée : soit une commande de création, soit (type session) un démarrage de séance.
   */
  record ParsedCreate(EntryType type, CreateEntryCommand command, StartSessionCommand start) {}

  private final Validator validator;
  private final EmotionCatalog emotions;
  private final TagFacade tags;

  EntryCommandFactory(Validator validator, EmotionCatalog emotions, TagFacade tags) {
    this.validator = validator;
    this.emotions = emotions;
    this.tags = tags;
  }

  // ---- création

  ParsedCreate parseCreate(CreateEntryRequest r) {
    List<FieldProblem> problems = new ArrayList<>();
    EntryType type = null;
    if (blank(r.type())) {
      problems.add(FieldProblem.required("type"));
    } else {
      type = EntryType.fromCode(r.type()).orElse(null);
      if (type == null) {
        problems.add(FieldProblem.invalidValue("type"));
      }
    }
    Visibility visibility = visibility(r.visibility(), problems);
    EntryStatus status = status(r.status(), problems);
    if (type == null) {
      throwIfAny(problems);
    }
    detailMatchesType(type, r.feeling(), r.medication(), r.session(), problems);
    if (type == EntryType.SESSION) {
      sessionCreateRules(r, problems);
    }
    TimeSpec spec = timeSpec(type, r.occurred(), problems);
    List<UUID> tagIds = type == EntryType.SESSION ? null : resolveTags(r.tags(), problems);

    FeelingDetail feeling =
        r.feeling() == null
            ? null
            : new FeelingDetail(r.feeling().emotionCode(), r.feeling().intensity(), null);
    MedicationDetail medication = medication(r.medication());
    SessionDetail session =
        r.session() == null
            ? null
            : new SessionDetail(
                SessionDetail.KIND_SESSION,
                r.session().modality(),
                r.session().durationMin(),
                null);

    validateContent(
        type,
        new EntryContent(
            r.title(),
            r.body(),
            visibility,
            status,
            timeForValidation(r.occurred()),
            feeling,
            medication,
            session),
        feeling == null ? null : feeling.emotionCode(),
        problems);
    throwIfAny(problems);

    if (type == EntryType.SESSION) {
      Instant from = r.occurred() == null ? null : r.occurred().from();
      return new ParsedCreate(type, null, new StartSessionCommand(r.session().modality(), from));
    }
    return new ParsedCreate(
        type,
        new CreateEntryCommand(
            type,
            r.sessionId(),
            r.title(),
            r.body(),
            visibility,
            status,
            spec,
            feeling,
            medication,
            session,
            tagIds == null ? List.of() : tagIds),
        null);
  }

  // ---- modification

  CorrectEntryCommand parsePatch(PatchEntryRequest r, EntryDetails current) {
    List<FieldProblem> problems = new ArrayList<>();
    EntryType type = current.entry().type();
    Visibility visibility = visibility(r.visibility(), problems);
    EntryStatus status = status(r.status(), problems);
    detailMatchesType(type, r.feeling(), r.medication(), r.session(), problems);
    TimeSpec spec = timeSpec(type, r.occurred(), problems);
    List<UUID> tagIds = type == EntryType.SESSION ? null : resolveTags(r.tags(), problems);

    FeelingDetail feeling = mergedFeeling(r.feeling(), current.feeling());
    MedicationDetail medication = mergedMedication(r.medication(), current.medication());
    SessionDetail session = mergedSession(r.session(), current.session());

    boolean emotionChanged =
        r.feeling() != null
            && !blank(r.feeling().emotionCode())
            && (current.feeling() == null
                || !r.feeling().emotionCode().equals(current.feeling().emotionCode()));
    validateContent(
        type,
        new EntryContent(
            r.title() != null ? r.title() : current.version().title(),
            r.body() != null ? r.body() : current.version().body(),
            visibility != null ? visibility : current.entry().visibility(),
            status != null ? status : current.entry().status(),
            r.occurred() != null ? timeForValidation(r.occurred()) : current.entry().time(),
            feeling,
            medication,
            session),
        emotionChanged ? feeling.emotionCode() : null,
        problems);
    throwIfAny(problems);

    return new CorrectEntryCommand(
        r.title(),
        r.body(),
        visibility,
        status,
        spec,
        r.feeling() == null ? null : feeling,
        r.medication() == null ? null : medication,
        r.session() == null ? null : session,
        r.reason(),
        tagIds);
  }

  // ---- champs simples

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static Visibility visibility(String code, List<FieldProblem> problems) {
    if (code == null) {
      return null;
    }
    return switch (code) {
      case "private" -> Visibility.PRIVATE;
      case "clinical" -> Visibility.CLINICAL;
      default -> {
        problems.add(FieldProblem.invalidValue("visibility"));
        yield null;
      }
    };
  }

  private static EntryStatus status(String code, List<FieldProblem> problems) {
    if (code == null) {
      return null;
    }
    return switch (code) {
      case "draft" -> EntryStatus.DRAFT;
      case "final" -> EntryStatus.FINAL;
      default -> {
        problems.add(FieldProblem.invalidValue("status"));
        yield null;
      }
    };
  }

  /** « Un seul détail selon le type » : un bloc de détail d'un autre type est refusé. */
  private static void detailMatchesType(
      EntryType type,
      EntryFeelingRequest feeling,
      EntryMedicationRequest medication,
      EntrySessionRequest session,
      List<FieldProblem> problems) {
    if (feeling != null && type != EntryType.FEELING) {
      problems.add(new FieldProblem("feeling", "DETAIL_NOT_ALLOWED"));
    }
    if (medication != null && type != EntryType.MEDICATION) {
      problems.add(new FieldProblem("medication", "DETAIL_NOT_ALLOWED"));
    }
    if (session != null && type != EntryType.SESSION) {
      problems.add(new FieldProblem("session", "DETAIL_NOT_ALLOWED"));
    }
  }

  /**
   * Un corps de type session passe par SessionService.start (verrou, 409 si une séance est déjà
   * ouverte), qui ne prend que la modalité et le début : la durée se donne à la clôture, une séance
   * n'est rattachée à aucune séance, et le contenu (title, body, tags) est refusé plutôt que perdu
   * en silence (le compte rendu se donne à la clôture, les tags par PATCH). status et visibility
   * sont ignorés : une séance est toujours finale et clinique.
   */
  private static void sessionCreateRules(CreateEntryRequest r, List<FieldProblem> problems) {
    if (r.sessionId() != null) {
      problems.add(new FieldProblem("sessionId", "SESSION_NOT_ALLOWED"));
    }
    if (r.session() != null && r.session().durationMin() != null) {
      problems.add(new FieldProblem("session.durationMin", "NOT_ALLOWED_ON_CREATE"));
    }
    if (!blank(r.title())) {
      problems.add(new FieldProblem("title", "NOT_ALLOWED_ON_CREATE"));
    }
    if (!blank(r.body())) {
      problems.add(new FieldProblem("body", "NOT_ALLOWED_ON_CREATE"));
    }
    if (r.tags() != null && !r.tags().isEmpty()) {
      problems.add(new FieldProblem("tags", "NOT_ALLOWED_ON_CREATE"));
    }
  }

  // ---- temps

  /**
   * Saisie du temps : from obligatoire (sauf phase de vie), to après from. Une situation sans fin
   * est « en cours » (to nul dans la réponse). La résolution des bornes reste au domaine.
   */
  private static TimeSpec timeSpec(
      EntryType type, EntryOccurredRequest o, List<FieldProblem> problems) {
    if (o == null) {
      return null;
    }
    Precision precision = Precision.EXACT;
    if (o.precision() != null) {
      try {
        precision = Precision.fromCode(o.precision());
      } catch (IllegalArgumentException e) {
        problems.add(FieldProblem.invalidValue("occurred.precision"));
      }
    }
    if (o.from() == null && precision != Precision.LIFE_PHASE) {
      problems.add(FieldProblem.required("occurred.from"));
    } else if (o.from() != null && o.to() != null && o.to().isBefore(o.from())) {
      problems.add(new FieldProblem("occurred.to", "INVALID_TIME"));
    }
    boolean ongoing =
        o.to() == null && type == EntryType.SITUATION && precision != Precision.LIFE_PHASE;
    return new TimeSpec(precision, o.from(), o.to(), o.label(), ongoing);
  }

  /** Temps factice pour la validation Jakarta (le vrai est résolu par le domaine). */
  private static TimeSpan timeForValidation(EntryOccurredRequest o) {
    return o == null ? null : PLACEHOLDER_TIME;
  }

  // ---- détails

  private static MedicationDetail medication(EntryMedicationRequest m) {
    return m == null
        ? null
        : new MedicationDetail(
            m.molecule(), m.dosage(), m.frequency(), m.prescriber(), m.periodFrom(), m.periodTo());
  }

  /** Champs absents repris du détail courant ; contexte et signes corporels sont conservés. */
  private static FeelingDetail mergedFeeling(EntryFeelingRequest r, FeelingDetail cur) {
    if (cur == null) {
      return r == null ? null : new FeelingDetail(r.emotionCode(), r.intensity(), null);
    }
    if (r == null) {
      return cur;
    }
    return new FeelingDetail(
        r.emotionCode() != null ? r.emotionCode() : cur.emotionCode(),
        cur.originalEmotionCode(),
        r.intensity() != null ? r.intensity() : cur.intensity(),
        null,
        null,
        cur.contextNote(),
        cur.bodilySigns());
  }

  private static MedicationDetail mergedMedication(EntryMedicationRequest r, MedicationDetail cur) {
    if (cur == null || r == null) {
      return r == null ? cur : medication(r);
    }
    return new MedicationDetail(
        r.molecule() != null ? r.molecule() : cur.molecule(),
        r.dosage() != null ? r.dosage() : cur.dosage(),
        r.frequency() != null ? r.frequency() : cur.frequency(),
        r.prescriber() != null ? r.prescriber() : cur.prescriber(),
        r.periodFrom() != null ? r.periodFrom() : cur.periodFrom(),
        r.periodTo() != null ? r.periodTo() : cur.periodTo());
  }

  /** Le genre (accueil, séance, contact) et la clôture ne se modifient pas par PATCH. */
  private static SessionDetail mergedSession(EntrySessionRequest r, SessionDetail cur) {
    if (cur == null || r == null) {
      return cur;
    }
    return new SessionDetail(
        cur.kind(),
        r.modality() != null ? r.modality() : cur.modality(),
        r.durationMin() != null ? r.durationMin() : cur.durationMin(),
        cur.closedAt());
  }

  // ---- validation

  /**
   * Champs minimaux du type (groupes Jakarta du domaine) puis émotion connue ; toutes les erreurs
   * sont cumulées, une par champ.
   *
   * @param emotionToCheck code d'émotion à vérifier dans le catalogue, nul : pas de vérification
   */
  private void validateContent(
      EntryType type, EntryContent content, String emotionToCheck, List<FieldProblem> problems) {
    validator
        .validate(content, EntryChecks.groupOf(type))
        .forEach(v -> problems.add(FieldProblem.of(v)));
    boolean emotionAlreadyWrong =
        problems.stream().anyMatch(p -> p.field().equals("feeling.emotionCode"));
    if (!blank(emotionToCheck)
        && !emotionAlreadyWrong
        && emotions.affectOf(emotionToCheck).isEmpty()) {
      problems.add(new FieldProblem("feeling.emotionCode", "UNKNOWN_EMOTION"));
    }
  }

  /** Libellés résolus en tags actifs du praticien, dans l'ordre ; nul si la liste est absente. */
  private List<UUID> resolveTags(List<String> labels, List<FieldProblem> problems) {
    if (labels == null) {
      return null;
    }
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < labels.size(); i++) {
      String field = "tags[" + i + "]";
      String label = labels.get(i);
      if (label == null || label.replace("#", "").isBlank()) {
        problems.add(FieldProblem.required(field));
        continue;
      }
      TagView tag = tags.resolve(label).orElse(null);
      if (tag == null) {
        problems.add(new FieldProblem(field, "UNKNOWN_TAG"));
      } else if (!ids.contains(tag.id())) {
        ids.add(tag.id());
      }
    }
    return ids;
  }

  /** Une erreur par champ : la première rencontrée l'emporte. */
  private static void throwIfAny(List<FieldProblem> problems) {
    if (problems.isEmpty()) {
      return;
    }
    Map<String, FieldProblem> byField = new LinkedHashMap<>();
    problems.forEach(p -> byField.putIfAbsent(p.field(), p));
    throw new EntryRequestException(new ArrayList<>(byField.values()));
  }
}
