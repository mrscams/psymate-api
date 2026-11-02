package com.psymate.overview;

import com.psymate.overview.OverviewDtos.OverviewDto;
import com.psymate.overview.OverviewPayload.WindowBlock;
import com.psymate.overview.OverviewProjector.Computed;
import com.psymate.overview.OverviewRepository.PatientState;
import com.psymate.overview.OverviewRepository.Stored;
import com.psymate.patients.api.PatientAccessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lecture et recalcul de la vue d'ensemble (A7, séquence 3). La projection est rafraîchie par les
 * abonnés d'événements ({@link #recompute}) ; à l'ouverture, {@link #overview} la sert telle quelle
 * si elle est à jour, sinon la recalcule et la réécrit : la vue ne sert jamais de donnée obsolète,
 * même si l'abonné asynchrone n'a pas encore tourné.
 */
@Service
class OverviewService {

  private static final Logger LOG = LoggerFactory.getLogger(OverviewService.class);
  static final int RECENT_ACTIVITY_SIZE = 10;

  private final OverviewProjector projector;
  private final OverviewRepository repository;
  private final NarrativeComposer composer;
  private final JsonMapper json;
  private final Clock clock;

  OverviewService(
      OverviewProjector projector,
      OverviewRepository repository,
      NarrativeComposer composer,
      JsonMapper json,
      Clock clock) {
    this.projector = projector;
    this.repository = repository;
    this.composer = composer;
    this.json = json;
    this.clock = clock;
  }

  /** Projection calculée ou relue, avec sa révision et son instant de calcul. */
  record Served(OverviewPayload payload, long sourceRev, Instant computedAt) {}

  /**
   * Recalcule et réécrit la projection d'un patient avec {@code source_rev = patient.clinical_rev}
   * lu dans la même transaction que le calcul. Idempotent : le résultat ne dépend que de l'état
   * courant de la base, jamais de l'événement qui a déclenché l'appel. Sans effet si le patient
   * n'existe pas.
   */
  @Transactional
  public Optional<Served> recompute(UUID patientId) {
    Instant now = clock.instant();
    long started = System.nanoTime();
    Optional<Computed> computed = projector.compute(patientId, now);
    if (computed.isEmpty()) {
      return Optional.empty();
    }
    int ms = (int) ((System.nanoTime() - started) / 1_000_000);
    Computed c = computed.get();
    int written = repository.upsert(patientId, serialize(c.payload()), c.sourceRev(), now, ms);
    LOG.info(
        "Vue d'ensemble calculée : patient={} sourceRev={} computeMs={} écrite={}",
        patientId,
        c.sourceRev(),
        ms,
        written == 1);
    return Optional.of(new Served(c.payload(), c.sourceRev(), now));
  }

  /**
   * Vue d'ensemble pour une fenêtre. Le patient est supposé déjà contrôlé par la garde de propriété
   * ; {@code recentActivity} est toujours lu en direct.
   */
  @Transactional
  public OverviewDto overview(UUID patientId, OverviewWindow window, Locale locale) {
    PatientState state =
        repository.patientState(patientId).orElseThrow(PatientAccessException::notFound);
    Served served = readFresh(patientId, state).or(() -> recompute(patientId)).orElseThrow();
    WindowBlock block = served.payload().windows().get(window.code());
    return new OverviewDto(
        window.code(),
        served.payload().facts(),
        served.payload().activeSituations(),
        block.trend(),
        block.emotions(),
        served.payload().drafts(),
        repository.recentActivity(patientId, RECENT_ACTIVITY_SIZE),
        composer.compose(served.payload().narrativeFacts(), locale),
        FeelingsAssembler.assemble(
            served.payload(), window, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)),
        served.payload().sinceLastSession(),
        served.payload().recentSessions(),
        served.computedAt(),
        served.sourceRev());
  }

  /** La ligne stockée, si elle est à jour et lisible. */
  private Optional<Served> readFresh(UUID patientId, PatientState state) {
    Optional<Stored> stored = repository.find(patientId);
    if (stored.isEmpty()) {
      return Optional.empty();
    }
    try {
      OverviewPayload payload = json.readValue(stored.get().payload(), OverviewPayload.class);
      if (!payload.hasAllBlocks()
          || !payload.windows().keySet().containsAll(windowCodes())
          || isStale(stored.get(), payload, state)) {
        return Optional.empty();
      }
      return Optional.of(new Served(payload, stored.get().sourceRev(), stored.get().computedAt()));
    } catch (JacksonException e) {
      LOG.warn("Projection illisible pour le patient {}, recalcul", patientId, e);
      return Optional.empty();
    }
  }

  private static java.util.List<String> windowCodes() {
    return java.util.Arrays.stream(OverviewWindow.values()).map(OverviewWindow::code).toList();
  }

  /**
   * En retard si une écriture clinique a eu lieu depuis (source_rev), si le calcul date d'un autre
   * jour UTC (la tendance et l'âge se calent sur aujourd'hui), ou si la fiche ou un drapeau a
   * changé depuis (ces écritures ne font pas bouger clinical_rev).
   */
  boolean isStale(Stored stored, OverviewPayload payload, PatientState state) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    return stored.sourceRev() < state.clinicalRev()
        || !LocalDate.ofInstant(stored.computedAt(), ZoneOffset.UTC).equals(today)
        || !Objects.equals(payload.patientChangedAt(), state.changedAt());
  }

  private String serialize(OverviewPayload payload) {
    return json.writeValueAsString(payload);
  }
}
