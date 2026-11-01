package com.psymate.seed;

import com.psymate.seed.Plan.LinkPlan;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.Plan.Practitioner;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Génère l'historique d'UN patient fictif, de façon déterministe (graine) : séances toutes les une
 * à trois semaines, situations qui s'ouvrent et se ferment, ressentis dont l'intensité suit les
 * situations actives, événements, médicaments avec changements de posologie, notes, liens
 * plausibles (matrice E5.3) et dates parfois imprécises.
 *
 * <p>Le nombre d'entrées (hors séances) et de liens visés est respecté : les séances se partagent
 * le budget d'entrées, et des liens complémentaires (de préférence vers les situations déjà très
 * liées, pour créer des pivots) comblent l'écart avec la cible de liens.
 */
final class PatientGenerator {

  /** Paramètres d'un patient. */
  record Config(
      String firstName,
      String lastName,
      String displayName,
      String pronoun,
      LocalDate birthDate,
      Practitioner practitioner,
      int entries,
      int links,
      int years,
      boolean medicated,
      List<String> tags) {}

  private static final int MAX_ACTIVE = 3;
  private static final Set<String> POSITIVE =
      Set.of(
          "serenity",
          "joy",
          "ecstasy",
          "acceptance",
          "trust",
          "admiration",
          "calm",
          "interest",
          "anticipation",
          "surprise",
          "amazement");

  // ---- état d'une situation
  private static final class Sit {
    String ref;
    Corpus.Theme theme;
    String title;
    Instant from;
    String precision; // exact, day, month, year, period
    Instant plannedClose; // null : reste ouverte
    Instant closedAt;
    boolean open = true;
    int degree;
  }

  /** Un traitement en cours (ou terminé) : entrée courante et situation traitée. */
  private static final class Run {
    Corpus.Course course;
    int doseIdx;
    String ref;
    String treatsRef;
    boolean stopped;
  }

  private enum MedType {
    START,
    CHANGE,
    STOP
  }

  private record MedAction(Run run, MedType type) {}

  private record Ctx(String ref, Instant ts, int durationMin, int index, boolean last) {}

  private final Random rnd;
  private final Instant end;
  private final Config cfg;
  private final PatientBuilder b = new PatientBuilder();

  private final List<Sit> sits = new ArrayList<>();
  private final Map<String, Sit> sitByRef = new HashMap<>();
  private final List<Run> runs = new ArrayList<>();
  private final Map<Integer, List<MedAction>> medActions = new HashMap<>();
  private final List<String> recent = new ArrayList<>(); // entrées récentes, toutes natures
  private final Map<String, String> lastOfKind = new HashMap<>();
  private final List<String[]> recentEvents = new ArrayList<>(); // {ref, index de séance}
  private Instant now;
  private int created;
  private int remaining;

  PatientGenerator(Random rnd, Instant end, Config cfg) {
    this.rnd = rnd;
    this.end = end;
    this.cfg = cfg;
    this.remaining = cfg.entries();
  }

  PatientPlan generate() {
    List<Instant> starts = schedule();
    int n = starts.size();
    planMedication(n);
    for (int i = 0; i < n; i++) {
      Instant ts = starts.get(i);
      now = ts;
      int duration = 20 + 5 * rnd.nextInt(9);
      String modality = modality();
      String ref = b.startSession(ts, modality);
      Ctx ctx = new Ctx(ref, ts, duration, i, i == n - 1);
      closeDue(ctx, n);
      int budget =
          ctx.last()
              ? remaining
              : Math.min(
                  remaining,
                  Math.max(
                      1,
                      (int)
                          Math.round(
                              (double) remaining / (n - i) * (0.6 + 0.8 * rnd.nextDouble()))));
      budget -= medicationActions(ctx);
      budget -= maybeOpenSituation(ctx, n);
      while (budget > 0 && remaining > 0) {
        double r = rnd.nextDouble();
        if (r < 0.50) {
          feeling(ctx);
        } else if (r < 0.80) {
          note(ctx);
        } else {
          event(ctx);
        }
        budget--;
      }
      b.closeSession(ref, duration, summary());
    }
    return new PatientPlan(
        cfg.displayName(),
        cfg.firstName(),
        cfg.lastName(),
        cfg.birthDate().toString(),
        cfg.pronoun(),
        cfg.tags(),
        cfg.practitioner(),
        b.steps());
  }

  // ---- calendrier

  private List<Instant> schedule() {
    List<Instant> list = new ArrayList<>();
    LocalDate endDay = end.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate day = endDay.minusDays(cfg.years() * 365L).plusDays(rnd.nextInt(14));
    while (day.isBefore(endDay)) {
      if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
        day = day.plusDays(1);
        continue;
      }
      list.add(day.atTime(8 + rnd.nextInt(11), 5 * rnd.nextInt(12)).toInstant(ZoneOffset.UTC));
      day = day.plusDays(7 + rnd.nextInt(15));
    }
    return list;
  }

  private String modality() {
    double r = rnd.nextDouble();
    return r < 0.80 ? "in_person" : r < 0.94 ? "video" : "phone";
  }

  private Instant inSession(Ctx ctx) {
    return ctx.ts().plus(rnd.nextInt(ctx.durationMin()), ChronoUnit.MINUTES);
  }

  /** Quelques jours avant la séance, à une heure de la journée. */
  private Instant daysBefore(Ctx ctx, int minDays, int maxDays) {
    LocalDate d =
        ctx.ts()
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .minusDays(minDays + rnd.nextInt(maxDays - minDays + 1));
    return d.atTime(7 + rnd.nextInt(15), 5 * rnd.nextInt(12)).toInstant(ZoneOffset.UTC);
  }

  // ---- situations

  private void closeDue(Ctx ctx, int n) {
    if (ctx.index() >= n - 2) {
      return; // les dernières séances laissent les situations ouvertes
    }
    for (Sit s : new ArrayList<>(sits)) {
      if (s.open && s.plannedClose != null && !s.plannedClose.isAfter(ctx.ts())) {
        close(s, ctx);
      }
    }
  }

  private void close(Sit s, Ctx ctx) {
    Instant to = ctx.ts().minus(rnd.nextInt(5 * 24 * 60), ChronoUnit.MINUTES);
    Instant minTo = s.from.plus(1, ChronoUnit.DAYS);
    if (to.isBefore(minTo)) {
      to = minTo.isAfter(ctx.ts()) ? ctx.ts() : minTo;
    }
    Body body = Body.of();
    if ("exact".equals(s.precision)) {
      body.occurred(s.from, to, "exact", null);
    } else {
      body.occurred(s.from, to, "period", FrDates.periodLabel(s.from, to));
    }
    body.reason("Situation terminée");
    b.patch(s.ref, body.build());
    s.open = false;
    s.closedAt = to;
  }

  private List<Sit> openSits() {
    List<Sit> list = new ArrayList<>();
    for (Sit s : sits) {
      if (s.open) {
        list.add(s);
      }
    }
    return list;
  }

  private int maybeOpenSituation(Ctx ctx, int n) {
    List<Sit> open = openSits();
    boolean forced = ctx.index() >= n - 2 && open.size() < 2;
    boolean wanted =
        ctx.index() == 0
            || (open.size() < MAX_ACTIVE && rnd.nextDouble() < (open.isEmpty() ? 0.6 : 0.22));
    if (remaining <= 0 || open.size() >= MAX_ACTIVE || !(wanted || forced)) {
      return 0;
    }
    openSituation(ctx, forced);
    return 1;
  }

  private Corpus.Theme pickTheme() {
    Set<String> activeTags = new HashSet<>();
    for (Sit s : openSits()) {
      activeTags.add(s.theme.tag());
    }
    List<Corpus.Theme> own = new ArrayList<>();
    List<Corpus.Theme> others = new ArrayList<>();
    for (Corpus.Theme t : Corpus.THEMES) {
      if (activeTags.contains(t.tag())) {
        continue;
      }
      (cfg.tags().contains(t.tag()) ? own : others).add(t);
    }
    List<Corpus.Theme> pool =
        !own.isEmpty() && (others.isEmpty() || rnd.nextDouble() < 0.7) ? own : others;
    return pool.get(rnd.nextInt(pool.size()));
  }

  private void openSituation(Ctx ctx, boolean perpetual) {
    Corpus.Theme theme = pickTheme();
    Sit s = new Sit();
    s.theme = theme;
    s.title = pick(theme.situationTitles());
    // précision et début
    double r = rnd.nextDouble();
    Instant at = inSession(ctx);
    Body body = Body.of().title(s.title).body(pick(theme.situationBodies()));
    LocalDate tsDay = ctx.ts().atZone(ZoneOffset.UTC).toLocalDate();
    if (r < 0.70) {
      s.precision = "exact";
      s.from = rnd.nextDouble() < 0.5 ? at : daysBefore(ctx, 1, 20);
      body.at(s.from);
    } else {
      double p = rnd.nextDouble();
      if (p < 0.25) {
        s.precision = "day";
        LocalDate d = tsDay.minusDays(1 + rnd.nextInt(30));
        s.from = d.atStartOfDay(ZoneOffset.UTC).toInstant();
        body.occurred(s.from, null, "day", null);
      } else if (p < 0.55) {
        s.precision = "month";
        LocalDate d = tsDay.minusDays(15 + rnd.nextInt(120)).withDayOfMonth(1);
        s.from = d.atStartOfDay(ZoneOffset.UTC).toInstant();
        body.occurred(s.from, null, "month", null);
      } else if (p < 0.70) {
        s.precision = "year";
        LocalDate d = LocalDate.of(tsDay.minusDays(200 + rnd.nextInt(600)).getYear(), 1, 1);
        s.from = d.atStartOfDay(ZoneOffset.UTC).toInstant();
        body.occurred(s.from, null, "year", null);
      } else {
        s.precision = "period";
        s.from = tsDay.minusDays(60 + rnd.nextInt(240)).atStartOfDay(ZoneOffset.UTC).toInstant();
        body.occurred(s.from, null, "period", FrDates.sinceLabel(s.from));
      }
    }
    if (s.from.isAfter(ctx.ts().plus(ctx.durationMin(), ChronoUnit.MINUTES))) {
      throw new IllegalStateException("Début de situation dans le futur");
    }
    s.plannedClose =
        perpetual
            ? null
            : ctx.ts().plus(30L * (1 + rnd.nextInt(18)) + rnd.nextInt(30), ChronoUnit.DAYS);
    if (cfg.tags().contains(theme.tag())) {
      body.tags(List.of(theme.tag()));
    }

    List<LinkPlan> links = new ArrayList<>();
    // une situation peut découler d'un événement récent, suivre une situation close, en déclencher
    // une autre
    String ev = recentEventRef(ctx.index(), 3);
    if (ev != null && rnd.nextDouble() < 0.30) {
      link(links, LinkRules.SITUATION, ev, "caused_by", "outgoing");
    }
    for (Sit other : sits) {
      if (!other.open
          && other.closedAt != null
          && other.closedAt.isAfter(ctx.ts().minus(100, ChronoUnit.DAYS))
          && rnd.nextDouble() < 0.25) {
        link(links, LinkRules.SITUATION, other.ref, "follows", "outgoing");
        break;
      }
    }
    for (Sit other : openSits()) {
      if (other.theme.negative() && rnd.nextDouble() < 0.20) {
        link(links, LinkRules.SITUATION, other.ref, "triggers", "incoming");
      } else if (rnd.nextDouble() < 0.15) {
        link(links, LinkRules.SITUATION, other.ref, "co_occurs", "outgoing");
      }
    }
    String ref = finish(ctx, LinkRules.SITUATION, body, links);
    s.ref = ref;
    sits.add(s);
    sitByRef.put(ref, s);
  }

  // ---- ressentis, événements, notes

  private void feeling(Ctx ctx) {
    List<Sit> active = openSits();
    Sit primary = active.isEmpty() || rnd.nextDouble() >= 0.85 ? null : weighted(active);
    String emotion;
    int intensity;
    if (active.isEmpty()) {
      emotion = pick(Corpus.CALM_EMOTIONS);
      intensity = 2 + rnd.nextInt(5);
    } else {
      Sit basis = primary != null ? primary : weighted(active);
      emotion =
          rnd.nextDouble() < 0.8
              ? pick(basis.theme.emotions())
              : pick(weighted(active).theme.emotions());
      int negatives = 0;
      for (Sit s : active) {
        if (s.theme.negative()) {
          negatives++;
        }
      }
      intensity =
          Math.max(0, Math.min(10, 2 + rnd.nextInt(3) + 2 * negatives + rnd.nextInt(3) - 1));
    }
    boolean positive = POSITIVE.contains(emotion);
    Body body = Body.of().feeling(emotion, intensity);
    if (rnd.nextDouble() < 0.5) {
      body.body(pick(positive ? Corpus.FEELING_POSITIVE : Corpus.FEELING_NEGATIVE));
    }
    time(body, ctx, 0.06, 0.30);
    List<LinkPlan> links = new ArrayList<>();
    if (primary != null && rnd.nextDouble() < 0.70) {
      link(links, LinkRules.FEELING, primary.ref, "triggers", "incoming");
    }
    String ev = recentEventRef(ctx.index(), 2);
    if (ev != null && !positive && rnd.nextDouble() < 0.25) {
      if (rnd.nextBoolean()) {
        link(links, LinkRules.FEELING, ev, "worsens", "incoming");
      } else {
        link(links, LinkRules.FEELING, ev, "caused_by", "outgoing");
      }
    }
    for (Run run : runs) {
      if (run.ref != null && !run.stopped) {
        double r = rnd.nextDouble();
        if (intensity <= 6 && r < 0.20) {
          link(links, LinkRules.FEELING, run.ref, "relieves", "incoming");
        } else if (r > 0.96) {
          link(links, LinkRules.FEELING, run.ref, "worsens", "incoming");
        }
      }
    }
    if (positive && rnd.nextDouble() < 0.06) {
      link(links, LinkRules.FEELING, ctx.ref(), "relieves", "incoming");
    }
    if (primary != null) {
      tagsFor(body, primary);
    } else {
      tagsFor(body, null);
    }
    finish(ctx, LinkRules.FEELING, body, links);
  }

  private void event(Ctx ctx) {
    List<Sit> active = openSits();
    Sit primary = active.isEmpty() ? null : weighted(active);
    Corpus.Theme theme =
        primary != null && rnd.nextDouble() < 0.8
            ? primary.theme
            : Corpus.THEMES[rnd.nextInt(Corpus.THEMES.length)];
    Body body = Body.of();
    boolean imprecise = rnd.nextDouble() < 0.33;
    boolean linkable = true;
    if (imprecise) {
      double p = rnd.nextDouble();
      LocalDate tsDay = ctx.ts().atZone(ZoneOffset.UTC).toLocalDate();
      if (p < 0.10) {
        body.title(pick(Corpus.CHILDHOOD_EVENTS)).lifePhase("enfance");
        linkable = false;
      } else {
        body.title(pick(theme.eventTitles()));
        if (p < 0.35) {
          body.year(tsDay.minusDays(400 + rnd.nextInt(3600)).getYear());
          linkable = false;
        } else if (p < 0.65) {
          body.month(tsDay.minusDays(30 + rnd.nextInt(370)));
          linkable = false;
        } else if (p < 0.85) {
          body.day(tsDay.minusDays(2 + rnd.nextInt(60)));
        } else {
          Instant from =
              tsDay.minusDays(60 + rnd.nextInt(900)).atStartOfDay(ZoneOffset.UTC).toInstant();
          Instant to = from.plus(10 + rnd.nextInt(110), ChronoUnit.DAYS);
          body.period(from, to, FrDates.periodLabel(from, to));
          linkable = false;
        }
      }
    } else {
      body.title(pick(theme.eventTitles()));
      time(body, ctx, 0, 0.25);
    }
    if (rnd.nextDouble() < 0.6) {
      body.body(pick(theme.eventBodies()));
    }
    List<LinkPlan> links = new ArrayList<>();
    if (primary != null && linkable && rnd.nextDouble() < 0.60) {
      link(links, LinkRules.EVENT, primary.ref, "part_of", "outgoing");
    }
    String last = lastOfKind.get(LinkRules.EVENT);
    if (last != null && linkable && rnd.nextDouble() < 0.15) {
      link(links, LinkRules.EVENT, last, "follows", "outgoing");
    }
    tagsFor(body, primary);
    String ref = finish(ctx, LinkRules.EVENT, body, links);
    if (linkable) {
      recentEvents.add(new String[] {ref, Integer.toString(ctx.index())});
    }
  }

  private void note(Ctx ctx) {
    Body body = Body.of();
    StringBuilder text = new StringBuilder(pick(Corpus.NOTES));
    int extra = rnd.nextInt(3); // 1 à 3 phrases
    for (int i = 0; i < extra; i++) {
      text.append(' ').append(pick(Corpus.NOTES));
    }
    body.body(text.toString());
    if (rnd.nextDouble() < 0.05) {
      body.day(ctx.ts().atZone(ZoneOffset.UTC).toLocalDate().minusDays(rnd.nextInt(4)));
    } else {
      body.at(inSession(ctx));
    }
    List<LinkPlan> links = new ArrayList<>();
    String f = lastOfKind.get(LinkRules.FEELING);
    String e = lastOfKind.get(LinkRules.EVENT);
    if (rnd.nextDouble() < 0.20) {
      String other = rnd.nextBoolean() && f != null ? f : e;
      if (other != null) {
        link(links, LinkRules.NOTE, other, "co_occurs", "outgoing");
      }
    }
    List<Sit> active = openSits();
    Sit primary = active.isEmpty() ? null : weighted(active);
    if (primary != null && rnd.nextDouble() < 0.15) {
      link(links, LinkRules.NOTE, primary.ref, "part_of", "outgoing");
    }
    if (rnd.nextDouble() < 0.05) {
      link(links, LinkRules.NOTE, ctx.ref(), "part_of", "outgoing");
    }
    tagsFor(body, primary);
    finish(ctx, LinkRules.NOTE, body, links);
  }

  /** Date d'un ressenti ou d'un événement : dans la séance, ou quelques jours avant. */
  private void time(Body body, Ctx ctx, double dayProbability, double beforeProbability) {
    double r = rnd.nextDouble();
    if (r < dayProbability) {
      body.day(ctx.ts().atZone(ZoneOffset.UTC).toLocalDate().minusDays(1 + rnd.nextInt(5)));
    } else if (r < dayProbability + beforeProbability) {
      body.at(daysBefore(ctx, 1, 6));
    } else {
      body.at(inSession(ctx));
    }
  }

  private void tagsFor(Body body, Sit primary) {
    if (cfg.tags().isEmpty() || rnd.nextDouble() >= 0.30) {
      return;
    }
    String tag =
        primary != null && cfg.tags().contains(primary.theme.tag()) && rnd.nextDouble() < 0.8
            ? primary.theme.tag()
            : cfg.tags().get(rnd.nextInt(cfg.tags().size()));
    body.tags(List.of(tag));
  }

  // ---- médicaments

  private void planMedication(int n) {
    if (!cfg.medicated() || n < 8) {
      return;
    }
    boolean multiple = cfg.years() >= 5;
    int idx = (int) (n * (0.10 + 0.25 * rnd.nextDouble()));
    while (idx < n - 3) {
      Corpus.Course c = pickCourse(false);
      Run run = new Run();
      run.course = c;
      schedule(idx, run, MedType.START);
      int cur = idx;
      for (int d = 1; d < c.dosages().length; d++) {
        cur += 4 + rnd.nextInt(Math.max(4, n / 6));
        if (cur >= n - 1) {
          break;
        }
        schedule(cur, run, MedType.CHANGE);
      }
      boolean stop = multiple || rnd.nextDouble() < 0.4;
      if (!stop) {
        break;
      }
      cur += 5 + rnd.nextInt(Math.max(5, n / 6));
      if (cur >= n - 1) {
        break;
      }
      schedule(cur, run, MedType.STOP);
      idx = cur + 5 + rnd.nextInt(15);
      if (!multiple) {
        break;
      }
    }
    if (rnd.nextDouble() < 0.4) {
      Corpus.Course c = pickCourse(true);
      Run run = new Run();
      run.course = c;
      int at = (int) (n * (0.2 + 0.6 * rnd.nextDouble()));
      schedule(at, run, MedType.START);
      if (rnd.nextBoolean() && at + 10 < n - 1) {
        schedule(
            at + 8 + rnd.nextInt(30 > n - at - 9 ? Math.max(1, n - at - 9) : 30),
            run,
            MedType.STOP);
      }
    }
  }

  private Corpus.Course pickCourse(boolean adjunct) {
    List<Corpus.Course> pool = new ArrayList<>();
    for (Corpus.Course c : Corpus.COURSES) {
      if (c.adjunct() == adjunct) {
        pool.add(c);
      }
    }
    return pool.get(rnd.nextInt(pool.size()));
  }

  private void schedule(int session, Run run, MedType type) {
    medActions.computeIfAbsent(session, k -> new ArrayList<>()).add(new MedAction(run, type));
    if (!runs.contains(run)) {
      runs.add(run);
    }
  }

  /** Exécute les actions de la séance ; renvoie le nombre d'entrées créées. */
  private int medicationActions(Ctx ctx) {
    List<MedAction> actions = medActions.get(ctx.index());
    if (actions == null) {
      return 0;
    }
    int made = 0;
    LocalDate date = ctx.ts().atZone(ZoneOffset.UTC).toLocalDate();
    for (MedAction a : actions) {
      Run run = a.run();
      if (a.type() == MedType.STOP) {
        if (run.ref != null && !run.stopped) {
          b.patch(run.ref, Body.of().medicationEnds(date).reason("Arrêt du traitement").build());
          run.stopped = true;
        }
        continue;
      }
      if (remaining <= 0) {
        continue;
      }
      if (a.type() == MedType.CHANGE) {
        if (run.ref == null || run.stopped) {
          continue;
        }
        b.patch(run.ref, Body.of().medicationEnds(date).reason("Changement de posologie").build());
        run.doseIdx++;
      }
      Corpus.Course c = run.course;
      String dosage = c.dosages()[Math.min(run.doseIdx, c.dosages().length - 1)];
      String prescriber =
          cfg.practitioner() == Practitioner.KARIM
              ? c.prescriber()
              : "Dr Lambert (médecin traitant)";
      String molecule = c.molecule();
      Body body =
          Body.of()
              .title(
                  Character.toUpperCase(molecule.charAt(0)) + molecule.substring(1) + " " + dosage)
              .body(
                  a.type() == MedType.START
                      ? "Début du traitement : "
                          + molecule
                          + " "
                          + dosage
                          + ", "
                          + c.frequency()
                          + "."
                      : "Posologie ajustée à " + dosage + ", " + c.frequency() + ".")
              .at(inSession(ctx))
              .medication(molecule, dosage, c.frequency(), prescriber, date);
      List<LinkPlan> links = new ArrayList<>();
      if (a.type() == MedType.START) {
        Sit target = openSits().isEmpty() ? null : weighted(openSits());
        run.treatsRef = target == null ? null : target.ref;
      } else if (run.treatsRef != null
          && !sitByRef.get(run.treatsRef).open
          && !openSits().isEmpty()) {
        run.treatsRef = weighted(openSits()).ref;
      }
      if (run.treatsRef != null) {
        link(links, LinkRules.MEDICATION, run.treatsRef, "treats", "outgoing");
      }
      run.ref = finish(ctx, LinkRules.MEDICATION, body, links);
      made++;
    }
    return made;
  }

  // ---- écriture d'une entrée, liens, budget

  /** Ajoute visibilité, liens complémentaires puis l'entrée ; met à jour les compteurs. */
  private String finish(Ctx ctx, String kind, Body body, List<LinkPlan> links) {
    if ((LinkRules.NOTE.equals(kind)
            || LinkRules.FEELING.equals(kind)
            || LinkRules.EVENT.equals(kind))
        && rnd.nextDouble() < 0.055) {
      body.privateEntry();
    }
    topUpLinks(links, kind);
    String ref = b.entry(ctx.ref(), kind, body.build(), links);
    created++;
    remaining--;
    for (LinkPlan l : links) {
      Sit s = sitByRef.get(l.targetRef());
      if (s != null) {
        s.degree++;
      }
    }
    lastOfKind.put(kind, ref);
    if (!LinkRules.SESSION.equals(kind)) {
      recent.add(ref);
      if (recent.size() > 40) {
        recent.remove(0);
      }
    }
    return ref;
  }

  private boolean link(List<LinkPlan> list, String kind, String target, String type, String dir) {
    LinkPlan lp = new LinkPlan(target, type, dir);
    if (!b.linkOk(kind, list, lp)) {
      return false;
    }
    list.add(lp);
    return true;
  }

  /** Comble l'écart avec la cible de liens, au rythme de création des entrées. */
  private void topUpLinks(List<LinkPlan> links, String kind) {
    if (cfg.entries() <= 0) {
      return;
    }
    double wanted = cfg.links() * (double) (created + 1) / cfg.entries() * 1.03;
    int deficit = (int) Math.ceil(wanted - b.linkCount() - links.size());
    int guard = 0;
    while (deficit > 0 && links.size() < PatientBuilder.MAX_LINKS && guard++ < 60) {
      if (extraLink(links, kind)) {
        deficit--;
      }
    }
  }

  private boolean extraLink(List<LinkPlan> links, String kind) {
    double r = rnd.nextDouble();
    if (r < 0.55) {
      Sit hub = hub();
      if (hub == null) {
        return false;
      }
      return switch (kind) {
        case LinkRules.FEELING ->
            rnd.nextDouble() < 0.75
                ? link(links, kind, hub.ref, "triggers", "incoming")
                : link(links, kind, hub.ref, "part_of", "outgoing");
        case LinkRules.EVENT ->
            rnd.nextDouble() < 0.6
                ? link(links, kind, hub.ref, "part_of", "outgoing")
                : link(links, kind, hub.ref, "triggers", "incoming");
        case LinkRules.NOTE ->
            rnd.nextDouble() < 0.6
                ? link(links, kind, hub.ref, "part_of", "outgoing")
                : link(links, kind, hub.ref, "co_occurs", "outgoing");
        case LinkRules.SITUATION ->
            rnd.nextDouble() < 0.5
                ? link(links, kind, hub.ref, "co_occurs", "outgoing")
                : link(links, kind, hub.ref, "triggers", "incoming");
        default -> link(links, kind, hub.ref, "treats", "outgoing");
      };
    }
    if (r < 0.75) {
      String previous = lastOfKind.get(kind);
      return previous != null && link(links, kind, previous, "follows", "outgoing");
    }
    if (recent.isEmpty()) {
      return false;
    }
    String other = recent.get(rnd.nextInt(recent.size()));
    return link(links, kind, other, "co_occurs", "outgoing");
  }

  /** Situation vers laquelle se lier en priorité : ouverte, déjà très liée. */
  private Sit hub() {
    double total = 0;
    double[] weights = new double[sits.size()];
    for (int i = 0; i < sits.size(); i++) {
      Sit s = sits.get(i);
      boolean eligible =
          s.open || (s.closedAt != null && s.closedAt.isAfter(now.minus(90, ChronoUnit.DAYS)));
      weights[i] = eligible ? (s.degree + 1) * (s.open ? 3.0 : 0.5) : 0;
      total += weights[i];
    }
    if (total <= 0) {
      return null;
    }
    double r = rnd.nextDouble() * total;
    for (int i = 0; i < weights.length; i++) {
      r -= weights[i];
      if (r <= 0 && weights[i] > 0) {
        return sits.get(i);
      }
    }
    return null;
  }

  private Sit weighted(List<Sit> list) {
    double total = 0;
    for (Sit s : list) {
      total += s.degree + 1;
    }
    double r = rnd.nextDouble() * total;
    for (Sit s : list) {
      r -= s.degree + 1;
      if (r <= 0) {
        return s;
      }
    }
    return list.get(list.size() - 1);
  }

  private String recentEventRef(int sessionIndex, int window) {
    for (int i = recentEvents.size() - 1; i >= 0; i--) {
      String[] e = recentEvents.get(i);
      if (sessionIndex - Integer.parseInt(e[1]) <= window) {
        return e[0];
      }
      break;
    }
    return null;
  }

  private String summary() {
    StringBuilder sb = new StringBuilder(pick(Corpus.NOTES));
    if (rnd.nextBoolean()) {
      sb.append(' ').append(pick(Corpus.SUMMARIES));
    }
    List<Sit> active = openSits();
    if (!active.isEmpty() && rnd.nextDouble() < 0.5) {
      sb.append(" On revient sur « ").append(weighted(active).title).append(" ».");
    }
    return sb.toString();
  }

  private String pick(String[] values) {
    return values[rnd.nextInt(values.length)];
  }
}
