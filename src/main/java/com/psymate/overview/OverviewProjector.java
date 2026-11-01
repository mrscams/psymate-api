package com.psymate.overview;

import com.psymate.overview.OverviewDtos.ActiveMedicationDto;
import com.psymate.overview.OverviewDtos.ActiveSituationDto;
import com.psymate.overview.OverviewDtos.DraftsDto;
import com.psymate.overview.OverviewDtos.EmotionsDto;
import com.psymate.overview.OverviewDtos.OpenSessionDto;
import com.psymate.overview.OverviewDtos.OverviewFactsDto;
import com.psymate.overview.OverviewDtos.OverviewFlagDto;
import com.psymate.overview.OverviewDtos.TrendDto;
import com.psymate.overview.OverviewDtos.TrendMarkerDto;
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

    List<FeelingSample> feelings = feelings(patientId, today, endExclusive);
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
    OverviewPayload payload =
        new OverviewPayload(
            state.changedAt(), facts, activeSituations(patientId), drafts(patientId), windows);
    return Optional.of(new Computed(payload, state.clinicalRev()));
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
  private List<FeelingSample> feelings(
      UUID patientId, LocalDate today, OffsetDateTime endExclusive) {
    OffsetDateTime from = startOfDay(OverviewWindow.earliestNeeded(today));
    return jdbc.sql(
            "select case when e.occurred_precision in ('month','year','period')"
                + "         and e.occurred_to is not null"
                + "       then e.occurred_from + (e.occurred_to - e.occurred_from) / 2"
                + "       else e.occurred_from end as at,"
                + " f.emotion_code, em.label_fr, f.intensity, f.valence"
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
              return new FeelingSample(
                  day.isAfter(today) ? today : day,
                  rs.getString("emotion_code"),
                  rs.getString("label_fr"),
                  rs.getInt("intensity"),
                  valence == null ? null : ((Number) valence).intValue());
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
