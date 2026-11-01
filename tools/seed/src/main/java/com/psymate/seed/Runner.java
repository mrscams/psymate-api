package com.psymate.seed;

import com.psymate.seed.ApiClient.Response;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.Plan.Practitioner;
import com.psymate.seed.StepExecutor.IncompleteException;
import com.psymate.seed.StepExecutor.Result;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Écrit un {@link Plan} par l'API. Chaque worker a ses propres sessions HTTP (une par praticien),
 * donc sa propre enveloppe de 600 requêtes par minute. Un patient dont le {@code displayName}
 * existe déjà chez le praticien est ignoré : relancer ne duplique rien.
 */
public final class Runner {

  /** Bilan d'une exécution. */
  public record Summary(
      int created,
      int alreadyPresent,
      int sessions,
      int entries,
      int links,
      int patches,
      Duration elapsed,
      Map<String, Practitioner> patientOwners) {}

  private final Accounts accounts;
  private final int workers;
  private final java.io.PrintStream out;

  public Runner(Accounts accounts, int workers, java.io.PrintStream out) {
    this.accounts = accounts;
    this.workers = Math.max(1, workers);
    this.out = out;
  }

  /** Sessions principales (une par praticien), réutilisables pour le contrôle final. */
  private final Map<Practitioner, ApiClient> mains = new ConcurrentHashMap<>();

  public ApiClient mainClient(Practitioner who) {
    return mains.computeIfAbsent(who, accounts::login);
  }

  public Summary run(Plan plan) {
    long start = System.nanoTime();
    Set<Practitioner> owners = new TreeSet<>();
    for (PatientPlan p : plan.patients()) {
      owners.add(p.practitioner());
    }
    for (Practitioner who : owners) {
      ApiClient main = mainClient(who);
      ensureTags(main, who, plan);
    }
    // idempotence : les patients déjà présents (par nom) ne sont pas recréés
    Map<Practitioner, Map<String, String>> existing = new HashMap<>();
    for (Practitioner who : owners) {
      existing.put(who, listPatients(mainClient(who)));
    }
    List<PatientPlan> todo = new ArrayList<>();
    Map<String, Practitioner> patientOwners = new LinkedHashMap<>();
    int already = 0;
    for (PatientPlan p : plan.patients()) {
      String id = existing.get(p.practitioner()).get(p.displayName());
      if (id != null) {
        already++;
        patientOwners.put(id, p.practitioner());
      } else {
        todo.add(p);
      }
    }
    out.printf(
        Locale.FRENCH,
        "Écriture : %d patients à créer, %d déjà présents, %d workers.%n",
        todo.size(),
        already,
        Math.min(workers, Math.max(1, todo.size())));

    AtomicInteger sessions = new AtomicInteger();
    AtomicInteger entries = new AtomicInteger();
    AtomicInteger links = new AtomicInteger();
    AtomicInteger patches = new AtomicInteger();
    AtomicInteger done = new AtomicInteger();
    AtomicLong progressMark = new AtomicLong();
    Map<String, Practitioner> created = new ConcurrentHashMap<>();
    java.util.concurrent.atomic.AtomicReference<RuntimeException> failure =
        new java.util.concurrent.atomic.AtomicReference<>();

    ThreadLocal<Map<Practitioner, ApiClient>> clients = ThreadLocal.withInitial(HashMap::new);
    ExecutorService pool =
        Executors.newFixedThreadPool(Math.min(workers, Math.max(1, todo.size())));
    List<Future<?>> futures = new ArrayList<>();
    for (PatientPlan p : todo) {
      futures.add(
          pool.submit(
              () -> {
                if (failure.get() != null) {
                  return;
                }
                long t0 = System.nanoTime();
                ApiClient api = clients.get().computeIfAbsent(p.practitioner(), accounts::login);
                try {
                  Result r =
                      StepExecutor.execute(
                          p,
                          (method, path, body) -> call(api, method, path, body),
                          n -> {
                            int total = entries.incrementAndGet();
                            long mark = total / 500;
                            long previous = progressMark.get();
                            if (mark > previous && progressMark.compareAndSet(previous, mark)) {
                              out.printf(Locale.FRENCH, "  … %d entrées écrites%n", total);
                            }
                          });
                  sessions.addAndGet(r.sessions());
                  links.addAndGet(r.links());
                  patches.addAndGet(r.patches());
                  created.put(r.patientId(), p.practitioner());
                  int n = done.incrementAndGet();
                  if (n % 10 == 0 || n == todo.size() || todo.size() <= 10) {
                    out.printf(
                        Locale.FRENCH,
                        "[%d/%d] %s : %d entrées, %d séances, %d liens (%.1f s)%n",
                        n,
                        todo.size(),
                        p.displayName(),
                        r.entries(),
                        r.sessions(),
                        r.links(),
                        (System.nanoTime() - t0) / 1e9);
                  }
                } catch (IncompleteException e) {
                  // dossier à moitié écrit : on l'archive pour qu'une relance le recrée proprement
                  api.delete("/api/patients/" + e.patientId());
                  failure.compareAndSet(
                      null,
                      new SeedException(
                          "Patient « "
                              + p.displayName()
                              + " » : "
                              + e.getMessage()
                              + " (dossier partiel archivé)",
                          e));
                } catch (RuntimeException e) {
                  failure.compareAndSet(null, e);
                }
              }));
    }
    pool.shutdown();
    for (Future<?> f : futures) {
      try {
        f.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new SeedException("Interrompu", e);
      } catch (ExecutionException e) {
        failure.compareAndSet(null, new SeedException(String.valueOf(e.getCause()), e.getCause()));
      }
    }
    if (failure.get() != null) {
      throw failure.get();
    }
    patientOwners.putAll(created);
    return new Summary(
        created.size(),
        already,
        sessions.get(),
        entries.get(),
        links.get(),
        patches.get(),
        Duration.ofNanos(System.nanoTime() - start),
        patientOwners);
  }

  private static Response call(ApiClient api, String method, String path, Object body) {
    return switch (method) {
      case "POST" -> api.post(path, body);
      case "PATCH" -> api.patch(path, body);
      case "GET" -> api.get(path);
      default -> throw new IllegalArgumentException(method);
    };
  }

  // ---- préparation

  /** Crée les tags manquants du praticien (par libellé, sans tenir compte de la casse). */
  private void ensureTags(ApiClient api, Practitioner who, Plan plan) {
    Set<String> wanted = new TreeSet<>();
    for (PatientPlan p : plan.patients()) {
      if (p.practitioner() == who) {
        wanted.addAll(p.tags());
      }
    }
    StepExecutor.ensureTags((method, path, body) -> call(api, method, path, body), wanted);
  }

  /** Patients actifs du praticien (toutes les pages), par nom d'affichage. */
  static Map<String, String> listPatients(ApiClient api) {
    Map<String, String> byName = new HashMap<>();
    String cursor = null;
    do {
      String path = "/api/patients?limit=100" + (cursor == null ? "" : "&cursor=" + encode(cursor));
      Map<String, Object> page = api.get(path, 200).json();
      if (page.get("items") instanceof List<?> items) {
        for (Object o : items) {
          if (o instanceof Map<?, ?> m && m.get("displayName") != null) {
            byName.put(m.get("displayName").toString(), m.get("id").toString());
          }
        }
      }
      cursor = page.get("nextCursor") == null ? null : page.get("nextCursor").toString();
    } while (cursor != null);
    return byName;
  }

  static String encode(String s) {
    return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
  }
}
