package com.psymate.overview;

import com.psymate.overview.OverviewDtos.ActiveMedicationDto;
import com.psymate.overview.OverviewDtos.ActiveSituationDto;
import com.psymate.overview.OverviewDtos.DraftsDto;
import com.psymate.overview.OverviewDtos.EmotionsDto;
import com.psymate.overview.OverviewDtos.OpenSessionDto;
import com.psymate.overview.OverviewDtos.OverviewFactsDto;
import com.psymate.overview.OverviewDtos.OverviewFlagDto;
import com.psymate.overview.OverviewDtos.RecentSessionDto;
import com.psymate.overview.OverviewDtos.SinceLastSessionDto;
import com.psymate.overview.OverviewDtos.SinceLastSessionEntryDto;
import com.psymate.overview.OverviewDtos.TrendDto;
import com.psymate.overview.OverviewDtos.TrendMarkerDto;
import com.psymate.overview.OverviewPayload.EmotionRef;
import com.psymate.overview.OverviewPayload.StoredFeelingPoint;
import com.psymate.overview.OverviewPayload.WindowBlock;
import com.psymate.overview.OverviewRepository.PatientState;
import com.psymate.overview.TrendCalculator.FeelingSample;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Calcul de la projection : lit en SQL direct (A5, module de projection) l'état du dossier et
 * assemble le {@link OverviewPayload}. Aucun contrôle d'accès ici : l'appelant est soit un abonné
 * d'événement, soit le contrôleur après la garde de propriété.
 *
 * <p>Règles : les entrées archivées sont exclues partout, les entrées privées incluses (le lecteur
 * est le propriétaire), les brouillons comptés dans {@code drafts} et aussi dans les autres blocs
 * (seul {@code archived_at} exclut). Les entrées {@code life_phase} sont exclues des courbes ; un
 * ressenti de précision {@code month}, {@code year} ou {@code period} ayant une fin est placé au
 * milieu de sa période (écran 2), jamais après aujourd'hui. Tous les jours sont des jours UTC.
 *
 * <p>Médicament actif (A6 « Vue d'ensemble », faits clés ; précision à reporter dans A6/G, la
 * documentation étant muette) : entrée {@code medication} non archivée dont la fin n'est ni connue
 * ni passée. La fin vient de {@code entry_medication.period_to} ; une période ouverte ({@code
 * period_from} sans {@code period_to}) est en cours ; sans aucune période, repli sur {@code
 * entry.occurred_to}, qui ne vaut comme fin que si le praticien l'a saisie (précision {@code exact}
 * ou {@code period} et {@code occurred_to} après {@code occurred_from}) : pour {@code day}, {@code
 * month} et {@code year}, clinical y pose la borne de la précision, pas une fin de traitement. Tri
 * par {@code period_from} décroissant (sinon {@code occurred_from}), une ligne par molécule (la
 * plus récente gagne). La même notion de fin sert aux marqueurs « end » de la tendance.
 */
@Component
class OverviewProjector {

  static final int SINCE_LAST_SESSION_SIZE = 20;
  static final int RECENT_SESSIONS_SIZE = 5;
  private static final int FIRST_LINE_MAX = 200;

  /** Résultat du calcul. */
  record Computed(OverviewPayload payload, long sourceRev) {}

  private final JdbcClient jdbc;
  private final OverviewRepository repository;

  OverviewProjector(JdbcClient jdbc, OverviewRepository repository) {
    this.jdbc = jdbc;
    this.repository = repository;
  }

  private record MedicationRow(
      UUID entryId,
      Instant from,
      Instant to,
      String precision,
      String molecule,
      String dosage,
      String frequency,
      String prescriber,
      LocalDate periodFrom,
      LocalDate periodTo) {

    /** Jour de début : la période si elle est saisie, sinon le jour de l'entrée. */
    LocalDate startDay() {
      return periodFrom != null ? periodFrom : dayOf(from);
    }

    /**
     * Jour de fin connu, ou nul si le traitement est en cours ou sans fin saisie (règle de classe).
     */
    LocalDate endDay() {
      if (periodTo != null) {
        return periodTo;
      }
      if (periodFrom != null || to == null) {
        return null;
      }
      boolean explicit =
          ("exact".equals(precision) || "period".equals(precision)) && to.isAfter(from);
      return explicit ? dayOf(to) : null;
    }
  }

  /** Ressenti non archivé placé sur un jour UTC ({@code at} = occurred_from de l'entrée). */
  private record FeelingRow(
      UUID entryId,
      LocalDate day,
      Instant at,
      String emotionCode,
      String family,
      String labelFr,
      String labelEn,
      int intensity,
      Integer valence,
      UUID sessionId) {}

  private record SessionRow(UUID entryId, Instant from, String title, String kind) {}

  private static LocalDate dayOf(Instant instant) {
    return LocalDate.ofInstant(instant, ZoneOffset.UTC);
  }

  private static OffsetDateTime startOfDay(LocalDate day) {
    return day.atStartOfDay().atOffset(ZoneOffset.UTC);
  }

  /**
   * Calcule la vue d'ensemble à l'instant {@code now}. La révision clinique est lue AVANT les
   * données : si une écriture s'intercale, la projection porte une révision trop ancienne et sera
   * recalculée à la prochaine ouverture, jamais l'inverse. Vide si le patient n'existe pas.
   */
  Optional<Computed> compute(UUID patientId, Instant now) {
    Optional<PatientState> found = repository.patientState(patientId);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    PatientState state = found.get();
    LocalDate today = dayOf(now);
    OffsetDateTime endExclusive = startOfDay(today.plusDays(1));

    List<FeelingRow> feelingRows = feelingRows(patientId, today, endExclusive);
    List<FeelingSample> feelings =
        feelingRows.stream()
            .map(
                r ->
                    new FeelingSample(
                        r.day(), r.emotionCode(), r.labelFr(), r.intensity(), r.valence()))
            .toList();
    List<MedicationRow> medications = medications(patientId, endExclusive);
    List<SessionRow> sessions = sessions(patientId, today, endExclusive);

    Map<String, WindowBlock> windows = new LinkedHashMap<>();
    for (OverviewWindow w : OverviewWindow.values()) {
      LocalDate start = w.start(today);
      TrendDto trend =
          new TrendDto(
              TrendCalculator.points(w.pointDates(today), feelings),
              markers(start, today, sessions, medications));
      EmotionsDto emotions =
          TrendCalculator.emotions(w.previousStart(today), start, today, feelings);
      windows.put(w.code(), new WindowBlock(trend, emotions));
    }

    OverviewFactsDto facts =
        new OverviewFactsDto(
            state.birthDate() == null ? null : Period.between(state.birthDate(), today).getYears(),
            followedSince(state),
            sessionCount(patientId),
            activeMedications(medications, today),
            flags(patientId));
    List<ActiveSituationDto> situations = activeSituations(patientId);
    OverviewPayload payload =
        new OverviewPayload(
            state.changedAt(),
            facts,
            situations,
            drafts(patientId),
            windows,
            narrativeFacts(patientId, today, now, facts, situations, feelingRows, sessions),
            storedPoints(today, feelingRows),
            emotionCatalog(feelingRows, today),
            sinceLastSession(patientId),
            recentSessions(patientId));
    return Optional.of(new Computed(payload, state.clinicalRev()));
  }

  // ---- récit (données structurées, M4-06)

  /**
   * Données du résumé « En bref » : aucune phrase, la langue se choisit à la lecture ({@link
   * NarrativeComposer}). Le rythme est l'intervalle médian entre les séances non archivées des 12
   * derniers mois ({@code sessions} couvre déjà cette période), tous genres confondus comme {@code
   * sessionCount}.
   */
  private NarrativeFacts narrativeFacts(
      UUID patientId,
      LocalDate today,
      Instant now,
      OverviewFactsDto facts,
      List<ActiveSituationDto> situations,
      List<FeelingRow> feelingRows,
      List<SessionRow> sessions) {
    List<NarrativeCalculator.Sample> samples =
        feelingRows.stream()
            .map(
                r ->
                    new NarrativeCalculator.Sample(
                        r.day(), r.emotionCode(), r.labelFr(), r.labelEn(), r.intensity()))
            .toList();
    LocalDate yearStart = OverviewWindow.M12.start(today);
    List<Instant> starts =
        sessions.stream()
            .map(SessionRow::from)
            .filter(from -> !dayOf(from).isBefore(yearStart))
            .toList();
    List<NarrativeFacts.MedicationFact> meds =
        facts.activeMedications().stream()
            .map(
                m ->
                    new NarrativeFacts.MedicationFact(
                        m.molecule(),
                        m.dosage(),
                        m.periodFrom() != null ? m.periodFrom() : dayOf(m.since())))
            .toList();
    List<NarrativeFacts.FlagFact> flags =
        facts.flags().stream()
            .map(f -> new NarrativeFacts.FlagFact(f.kind(), f.reason(), dayOf(f.setAt())))
            .toList();
    return new NarrativeFacts(
        facts.followedSince(),
        facts.sessionCount(),
        NarrativeCalculator.rhythmDays(starts),
        NarrativeCalculator.trend(today, samples),
        NarrativeCalculator.dominant(today, samples).orElse(null),
        situations.stream()
            .map(ActiveSituationDto::title)
            .filter(t -> t != null && !t.isBlank())
            .toList(),
        uncommentedEvent(patientId, today),
        meds,
        flags);
  }

  /**
   * Événement ({@code type = 'event'}) non archivé des 30 derniers jours que rien ne commente : ni
   * lien accepté (dans un sens ou l'autre), ni ressenti non archivé commençant à son début ou
   * après. Le plus récent.
   */
  private NarrativeFacts.UncommentedEvent uncommentedEvent(UUID patientId, LocalDate today) {
    return jdbc.sql(
            "select v.title, e.occurred_from from entry e"
                + " left join entry_version v on v.id = e.current_version_id"
                + " where e.patient_id = :p and e.type = 'event' and e.archived_at is null"
                + " and e.occurred_from >= :from and e.occurred_from < :end"
                + " and not exists (select 1 from link l where l.status = 'accepted'"
                + "   and (l.source_id = e.id or l.target_id = e.id))"
                + " and not exists (select 1 from entry f join entry_feeling ef on ef.entry_id = f.id"
                + "   where f.patient_id = :p and f.archived_at is null"
                + "   and f.occurred_from >= e.occurred_from)"
                + " order by e.occurred_from desc, e.id desc limit 1")
        .param("p", patientId)
        .param("from", startOfDay(today.minusDays(NarrativeCalculator.WINDOW_DAYS - 1L)))
        .param("end", startOfDay(today.plusDays(1)))
        .query(
            (rs, i) ->
                new NarrativeFacts.UncommentedEvent(
                    rs.getString("title"), dayOf(OverviewRepository.instant(rs, "occurred_from"))))
        .optional()
        .orElse(null);
  }

  // ---- ressentis réels, depuis la dernière séance, séances récentes (M4-06)

  /** Un point par ressenti des 12 derniers mois, du plus ancien au plus récent. */
  private static List<StoredFeelingPoint> storedPoints(LocalDate today, List<FeelingRow> rows) {
    LocalDate start = OverviewWindow.M12.start(today);
    return rows.stream()
        .filter(r -> !r.day().isBefore(start) && !r.day().isAfter(today))
        .sorted(
            Comparator.comparing(FeelingRow::day)
                .thenComparing(FeelingRow::at)
                .thenComparing(r -> r.entryId().toString()))
        .map(
            r ->
                new StoredFeelingPoint(
                    r.entryId(),
                    r.day(),
                    r.at(),
                    r.emotionCode(),
                    r.intensity(),
                    r.valence(),
                    r.sessionId()))
        .toList();
  }

  /** Émotions présentes parmi les points stockés (libellés FR et EN, famille). */
  private static List<EmotionRef> emotionCatalog(List<FeelingRow> rows, LocalDate today) {
    LocalDate start = OverviewWindow.M12.start(today);
    Map<String, EmotionRef> byCode = new java.util.TreeMap<>();
    for (FeelingRow r : rows) {
      if (!r.day().isBefore(start) && !r.day().isAfter(today)) {
        byCode.putIfAbsent(
            r.emotionCode(), new EmotionRef(r.emotionCode(), r.family(), r.labelFr(), r.labelEn()));
      }
    }
    return List.copyOf(byCode.values());
  }

  /**
   * Référence : séance clôturée non archivée la plus récente (par {@code occurred_from}). Entrées :
   * non archivées, hors séances, non rattachées à cette séance, commençant à son début ou après, 20
   * au plus, de la plus récente à la plus ancienne. Sans séance clôturée : les 20 dernières entrées
   * non archivées hors séances.
   */
  private SinceLastSessionDto sinceLastSession(UUID patientId) {
    record Last(UUID id, Instant at) {}
    Last last =
        jdbc.sql(
                "select e.id, e.occurred_from from entry e"
                    + " join entry_session s on s.entry_id = e.id"
                    + " where e.patient_id = :p and e.archived_at is null"
                    + " and s.closed_at is not null"
                    + " order by e.occurred_from desc, e.id desc limit 1")
            .param("p", patientId)
            .query(
                (rs, i) ->
                    new Last(
                        rs.getObject("id", UUID.class),
                        OverviewRepository.instant(rs, "occurred_from")))
            .optional()
            .orElse(null);
    String sql =
        "select e.id, e.type, v.title, e.occurred_from, e.status, e.visibility, e.session_id"
            + " from entry e left join entry_version v on v.id = e.current_version_id"
            + " where e.patient_id = :p and e.archived_at is null and e.type <> 'session'"
            + (last == null
                ? ""
                : " and e.session_id is distinct from :last and e.occurred_from >= :at")
            + " order by e.occurred_from desc, e.id desc limit "
            + SINCE_LAST_SESSION_SIZE;
    JdbcClient.StatementSpec spec = jdbc.sql(sql).param("p", patientId);
    if (last != null) {
      spec = spec.param("last", last.id()).param("at", OverviewRepository.odt(last.at()));
    }
    List<SinceLastSessionEntryDto> entries =
        spec.query(
                (rs, i) ->
                    new SinceLastSessionEntryDto(
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getString("title"),
                        OverviewRepository.instant(rs, "occurred_from"),
                        rs.getString("status"),
                        rs.getString("visibility"),
                        rs.getObject("session_id", UUID.class)))
            .list();
    return new SinceLastSessionDto(
        last == null ? null : last.id(), last == null ? null : last.at(), entries);
  }

  /**
   * Les 5 séances non archivées les plus récentes. Le compte rendu entier (version désignée par
   * {@code summary_version_id}, sinon version courante) n'est conservé que pour la première ; les
   * autres n'en gardent que la première ligne.
   */
  private List<RecentSessionDto> recentSessions(UUID patientId) {
    record Row(RecentSessionDto base, String body) {}
    List<Row> rows =
        jdbc.sql(
                "select e.id, e.occurred_from, s.kind, s.modality, s.duration_min, s.closed_at,"
                    + " cv.title, coalesce(sv.body, cv.body) as body,"
                    + " (select count(*) from entry c where c.session_id = e.id"
                    + "   and c.archived_at is null) as entry_count"
                    + " from entry e join entry_session s on s.entry_id = e.id"
                    + " left join entry_version cv on cv.id = e.current_version_id"
                    + " left join entry_version sv on sv.id = s.summary_version_id"
                    + " where e.patient_id = :p and e.archived_at is null"
                    + " order by e.occurred_from desc, e.id desc limit "
                    + RECENT_SESSIONS_SIZE)
            .param("p", patientId)
            .query(
                (rs, i) -> {
                  String body = rs.getString("body");
                  return new Row(
                      new RecentSessionDto(
                          rs.getObject("id", UUID.class),
                          OverviewRepository.instant(rs, "occurred_from"),
                          rs.getString("kind"),
                          rs.getString("modality"),
                          (Integer) rs.getObject("duration_min"),
                          OverviewRepository.instant(rs, "closed_at"),
                          rs.getString("title"),
                          rs.getInt("entry_count"),
                          firstLine(body),
                          null),
                      body);
                })
            .list();
    List<RecentSessionDto> sessions = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      RecentSessionDto s = rows.get(i).base();
      String body = rows.get(i).body();
      sessions.add(
          i == 0 && body != null && !body.isBlank()
              ? new RecentSessionDto(
                  s.entryId(),
                  s.occurredFrom(),
                  s.kind(),
                  s.modality(),
                  s.durationMin(),
                  s.closedAt(),
                  s.title(),
                  s.entryCount(),
                  s.summaryFirstLine(),
                  body)
              : s);
    }
    return sessions;
  }

  /** Première ligne non vide du texte (200 caractères au plus), nulle s'il n'y en a pas. */
  static String firstLine(String text) {
    if (text == null) {
      return null;
    }
    for (String line : text.split("\\R")) {
      String stripped = line.strip();
      if (!stripped.isEmpty()) {
        return stripped.length() <= FIRST_LINE_MAX
            ? stripped
            : stripped.substring(0, FIRST_LINE_MAX - 1).stripTrailing() + "…";
      }
    }
    return null;
  }

  // ---- faits

  private static LocalDate followedSince(PatientState state) {
    if (state.followUpSince() != null) {
      try {
        return LocalDate.parse(state.followUpSince());
      } catch (java.time.format.DateTimeParseException e) {
        // valeur libre mal formée dans follow_up : on retombe sur la date de création du dossier
      }
    }
    return dayOf(state.createdAt());
  }

  private int sessionCount(UUID patientId) {
    return jdbc.sql(
            "select count(*) from entry where patient_id = :p and type = 'session'"
                + " and archived_at is null")
        .param("p", patientId)
        .query(Integer.class)
        .single();
  }

  private List<OverviewFlagDto> flags(UUID patientId) {
    return jdbc.sql(
            "select kind, reason, set_at from patient_flag"
                + " where patient_id = :p and cleared_at is null order by set_at, kind")
        .param("p", patientId)
        .query(
            (rs, i) ->
                new OverviewFlagDto(
                    rs.getString("kind"),
                    rs.getString("reason"),
                    OverviewRepository.instant(rs, "set_at")))
        .list();
  }

  private List<MedicationRow> medications(UUID patientId, OffsetDateTime endExclusive) {
    return jdbc.sql(
            "select e.id, e.occurred_from, e.occurred_to, e.occurred_precision, m.molecule,"
                + " m.dosage, m.frequency, m.prescriber, m.period_from, m.period_to"
                + " from entry e join entry_medication m on m.entry_id = e.id"
                + " where e.patient_id = :p and e.archived_at is null"
                + " and e.occurred_from < :end order by e.occurred_from, e.id")
        .param("p", patientId)
        .param("end", endExclusive)
        .query(
            (rs, i) ->
                new MedicationRow(
                    rs.getObject("id", UUID.class),
                    OverviewRepository.instant(rs, "occurred_from"),
                    OverviewRepository.instant(rs, "occurred_to"),
                    rs.getString("occurred_precision"),
                    rs.getString("molecule"),
                    rs.getString("dosage"),
                    rs.getString("frequency"),
                    rs.getString("prescriber"),
                    rs.getObject("period_from", LocalDate.class),
                    rs.getObject("period_to", LocalDate.class)))
        .list();
  }

  /**
   * Médicaments dont la fin n'est ni connue ni passée (règle de classe), du plus récent au plus
   * ancien, une ligne par molécule.
   */
  private static List<ActiveMedicationDto> activeMedications(
      List<MedicationRow> rows, LocalDate today) {
    Map<String, MedicationRow> byMolecule = new LinkedHashMap<>();
    rows.stream()
        .filter(m -> m.endDay() == null || !m.endDay().isBefore(today))
        .sorted(
            Comparator.comparing(MedicationRow::startDay)
                .thenComparing(MedicationRow::from)
                .reversed())
        .forEach(m -> byMolecule.putIfAbsent(moleculeKey(m.molecule()), m));
    return byMolecule.values().stream()
        .map(
            m ->
                new ActiveMedicationDto(
                    m.entryId(),
                    m.molecule(),
                    m.dosage(),
                    m.frequency(),
                    m.prescriber(),
                    m.from(),
                    m.periodFrom(),
                    m.periodTo()))
        .toList();
  }

  private static String moleculeKey(String molecule) {
    return molecule == null ? "" : molecule.trim().toLowerCase(java.util.Locale.ROOT);
  }

  // ---- situations actives

  private List<ActiveSituationDto> activeSituations(UUID patientId) {
    return jdbc.sql(
            "select e.id, v.title, e.occurred_from, e.link_count, lf.cnt, lf.avg_intensity"
                + " from entry e left join entry_version v on v.id = e.current_version_id"
                + " left join lateral ("
                + "   select count(*) as cnt, avg(x.intensity) as avg_intensity from ("
                + "     select distinct o.id, f.intensity from link l"
                + "     join entry o on o.id = case when l.source_id = e.id"
                + "       then l.target_id else l.source_id end"
                + "     join entry_feeling f on f.entry_id = o.id"
                + "     where l.status = 'accepted' and (l.source_id = e.id or l.target_id = e.id)"
                + "       and o.archived_at is null) x"
                + " ) lf on true"
                + " where e.patient_id = :p and e.type = 'situation' and e.archived_at is null"
                + " and e.occurred_to is null"
                + " order by e.link_count desc, e.occurred_from desc, e.id")
        .param("p", patientId)
        .query(
            (rs, i) -> {
              double avg = rs.getDouble("avg_intensity");
              boolean none = rs.wasNull();
              return new ActiveSituationDto(
                  rs.getObject("id", UUID.class),
                  rs.getString("title"),
                  OverviewRepository.instant(rs, "occurred_from"),
                  rs.getInt("link_count"),
                  rs.getInt("cnt"),
                  none ? null : TrendCalculator.round(avg));
            })
        .list();
  }

  // ---- brouillons et séance ouverte

  private DraftsDto drafts(UUID patientId) {
    int count =
        jdbc.sql(
                "select count(*) from entry where patient_id = :p and status = 'draft'"
                    + " and archived_at is null")
            .param("p", patientId)
            .query(Integer.class)
            .single();
    OpenSessionDto open =
        jdbc.sql(
                "select e.id, e.occurred_from from entry e"
                    + " join entry_session s on s.entry_id = e.id"
                    + " where e.patient_id = :p and e.archived_at is null and s.closed_at is null"
                    + " order by e.occurred_from desc, e.id desc limit 1")
            .param("p", patientId)
            .query(
                (rs, i) ->
                    new OpenSessionDto(
                        rs.getObject("id", UUID.class),
                        OverviewRepository.instant(rs, "occurred_from")))
            .optional()
            .orElse(null);
    return new DraftsDto(count, open);
  }

  // ---- tendance

  /** Ressentis de la fenêtre la plus large (précédente de 12m, plus la moyenne glissante). */
  private List<FeelingRow> feelingRows(
      UUID patientId, LocalDate today, OffsetDateTime endExclusive) {
    OffsetDateTime from = startOfDay(OverviewWindow.earliestNeeded(today));
    return jdbc.sql(
            "select e.id, e.session_id, e.occurred_from,"
                + " case when e.occurred_precision in ('month','year','period')"
                + "         and e.occurred_to is not null"
                + "       then e.occurred_from + (e.occurred_to - e.occurred_from) / 2"
                + "       else e.occurred_from end as at,"
                + " f.emotion_code, em.family, em.label_fr, em.label_en, f.intensity, f.valence"
                + " from entry e join entry_feeling f on f.entry_id = e.id"
                + " join emotion em on em.code = f.emotion_code"
                + " where e.patient_id = :p and e.archived_at is null"
                + " and e.occurred_precision <> 'life_phase'"
                + " and e.occurred_from < :end"
                + " and coalesce(e.occurred_to, e.occurred_from) >= :from")
        .param("p", patientId)
        .param("from", from)
        .param("end", endExclusive)
        .query(
            (rs, i) -> {
              Object valence = rs.getObject("valence");
              // Le milieu d'une période en cours (mois courant) peut dépasser aujourd'hui :
              // l'échantillon est alors plafonné au jour courant pour rester dans la fenêtre.
              LocalDate day = dayOf(OverviewRepository.instant(rs, "at"));
              return new FeelingRow(
                  rs.getObject("id", UUID.class),
                  day.isAfter(today) ? today : day,
                  OverviewRepository.instant(rs, "occurred_from"),
                  rs.getString("emotion_code"),
                  rs.getString("family"),
                  rs.getString("label_fr"),
                  rs.getString("label_en"),
                  rs.getInt("intensity"),
                  valence == null ? null : ((Number) valence).intValue(),
                  rs.getObject("session_id", UUID.class));
            })
        .list();
  }

  private List<SessionRow> sessions(UUID patientId, LocalDate today, OffsetDateTime endExclusive) {
    LocalDate earliest = today;
    for (OverviewWindow w : OverviewWindow.values()) {
      if (w.start(today).isBefore(earliest)) {
        earliest = w.start(today);
      }
    }
    return jdbc.sql(
            "select e.id, e.occurred_from, v.title, s.kind from entry e"
                + " join entry_session s on s.entry_id = e.id"
                + " left join entry_version v on v.id = e.current_version_id"
                + " where e.patient_id = :p and e.archived_at is null"
                + " and e.occurred_from >= :from and e.occurred_from < :end"
                + " order by e.occurred_from, e.id")
        .param("p", patientId)
        .param("from", startOfDay(earliest))
        .param("end", endExclusive)
        .query(
            (rs, i) ->
                new SessionRow(
                    rs.getObject("id", UUID.class),
                    OverviewRepository.instant(rs, "occurred_from"),
                    rs.getString("title"),
                    rs.getString("kind")))
        .list();
  }

  /**
   * Toutes les séances et tous les changements de médicament (début, et fin si elle est connue et
   * distincte du début) dont le jour tombe dans {@code [start, today]}. Le libellé d'une séance est
   * son titre, nul sinon : le client traduit {@code sessionKind} (pas de texte figé en base).
   */
  private static List<TrendMarkerDto> markers(
      LocalDate start, LocalDate today, List<SessionRow> sessions, List<MedicationRow> meds) {
    List<TrendMarkerDto> markers = new ArrayList<>();
    for (SessionRow s : sessions) {
      LocalDate day = dayOf(s.from());
      if (inRange(day, start, today)) {
        String label = s.title() != null && !s.title().isBlank() ? s.title() : null;
        markers.add(new TrendMarkerDto(day, "session", s.entryId(), label, s.kind(), null));
      }
    }
    for (MedicationRow m : meds) {
      String label =
          m.dosage() == null || m.dosage().isBlank()
              ? m.molecule()
              : m.molecule() + " " + m.dosage();
      LocalDate from = m.startDay();
      if (inRange(from, start, today)) {
        markers.add(new TrendMarkerDto(from, "medication", m.entryId(), label, null, "start"));
      }
      LocalDate end = m.endDay();
      if (end != null && !end.equals(from) && inRange(end, start, today)) {
        markers.add(new TrendMarkerDto(end, "medication", m.entryId(), label, null, "end"));
      }
    }
    markers.sort(
        Comparator.comparing(TrendMarkerDto::date)
            .thenComparing(TrendMarkerDto::kind)
            .thenComparing(m -> m.entryId().toString())
            .thenComparing(m -> m.change() == null ? "" : m.change()));
    return markers;
  }

  private static boolean inRange(LocalDate day, LocalDate start, LocalDate today) {
    return !day.isBefore(start) && !day.isAfter(today);
  }
}
