package com.psymate.seed;

import com.psymate.seed.ApiClient.Response;
import com.psymate.seed.Plan.Practitioner;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Point d'entrée du seed : {@code java -cp tools/seed/target/classes com.psymate.seed.Seed --size
 * small|medium|demo}. Écrit des patients fictifs par l'API REST d'une instance LOCALE, jamais
 * autrement (E5.5).
 *
 * <p>Codes de sortie : 0 tout est bon et le contrôle final passe, 1 erreur d'exécution, 2 garde-fou
 * ou option invalide (rien n'a été écrit).
 */
public final class Seed {

  private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

  private Seed() {}

  /** Option invalide ou garde-fou franchi (code de sortie 2). */
  private static final class Refused extends RuntimeException {
    Refused(String message) {
      super(message);
    }
  }

  public static void main(String[] args) {
    PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
    try {
      System.exit(run(args, out));
    } catch (Refused e) {
      err.println(e.getMessage());
      System.exit(2);
    } catch (SeedException e) {
      err.println("Erreur : " + e.getMessage());
      System.exit(1);
    } catch (RuntimeException e) {
      e.printStackTrace(err);
      System.exit(1);
    }
  }

  static int run(String[] args, PrintStream out) {
    Map<String, String> options = parse(args);
    String size = options.get("size");
    String api = options.getOrDefault("api", "http://localhost:8080");
    String mailpit = options.getOrDefault("mailpit", "http://localhost:8025");
    long seed =
        parseLong(options.getOrDefault("seed", Long.toString(Scenario.DEFAULT_SEED)), "--seed");
    if (size == null || !Set.of("small", "medium", "demo").contains(size)) {
      throw new Refused("Option --size obligatoire : small, medium ou demo.\n" + usage());
    }
    guard(api, mailpit);

    Plan plan =
        switch (size) {
          case "small" -> Scenario.small(seed);
          case "medium" -> Scenario.medium(seed);
          default -> Scenario.demo();
        };
    List<String> problems = Scenario.validate(plan);
    if (!problems.isEmpty()) {
      problems.stream().limit(20).forEach(p -> System.err.println("Plan invalide : " + p));
      throw new SeedException(
          "Le plan généré ne respecte pas E (" + problems.size() + " violation(s)).");
    }
    int workers =
        options.containsKey("workers")
            ? (int) parseLong(options.get("workers"), "--workers")
            : Math.min(8, plan.patients().size());
    out.println("Seed « " + size + " » (graine " + seed + ") : " + Scenario.stats(plan).describe());

    Accounts accounts = new Accounts(api, mailpit);
    Runner runner = new Runner(accounts, workers, out);
    Runner.Summary summary = runner.run(plan);
    out.printf(
        Locale.FRENCH,
        "Résumé : %d patients créés, %d déjà présents, %d séances, %d entrées, %d liens, %d corrections, en %s.%n",
        summary.created(),
        summary.alreadyPresent(),
        summary.sessions(),
        summary.entries(),
        summary.links(),
        summary.patches(),
        pretty(summary.elapsed()));

    Map<String, ApiClient> toCheck = new LinkedHashMap<>();
    for (Map.Entry<String, Practitioner> e : summary.patientOwners().entrySet()) {
      toCheck.put(e.getKey(), runner.mainClient(e.getValue()));
    }
    Verifier.Report report = Verifier.verify(toCheck);
    out.println(report.describe());
    return report.ok() ? 0 : 1;
  }

  // ---- garde-fous

  private static void guard(String api, String mailpit) {
    requireLocal(api, "--api");
    requireLocal(mailpit, "--mailpit");
    // L'API d'abord : sans API locale, c'est ce message-là que l'on attend (critère C2 M7-01).
    ApiClient client = new ApiClient(api);
    String unreachable =
        "L'API locale ne répond pas sur " + api + " : lance make up puis make run.";
    Response health;
    try {
      health = client.get("/actuator/health");
    } catch (SeedException e) {
      throw new Refused(unreachable);
    }
    if (health.status() != 200) {
      throw new Refused(unreachable);
    }
    Response info;
    try {
      info = client.get("/actuator/info");
    } catch (SeedException e) {
      throw new Refused(unreachable);
    }
    Object profile = info.status() == 200 ? info.json().get("profile") : null;
    if (!"local".equals(profile)) {
      throw new Refused("L'API ne tourne pas en profil local : seed refusé.");
    }
    ApiClient mail = new ApiClient(mailpit);
    boolean mailpitUp;
    try {
      mailpitUp = mail.get("/api/v1/info").status() == 200;
    } catch (SeedException e) {
      mailpitUp = false;
    }
    if (!mailpitUp) {
      throw new Refused(
          "Mailpit ne répond pas sur " + mailpit + " : lance make up (confirmation des e-mails).");
    }
  }

  private static void requireLocal(String url, String option) {
    String host;
    try {
      host = URI.create(url).getHost();
    } catch (IllegalArgumentException e) {
      throw new Refused(option + " : URL invalide (" + url + ").");
    }
    if (host == null || !LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
      throw new Refused(
          option
              + " : l'hôte « "
              + host
              + " » n'est pas local ; le seed n'écrit que sur localhost, 127.0.0.1 ou ::1.");
    }
  }

  // ---- options

  private static Map<String, String> parse(String[] args) {
    Map<String, String> options = new LinkedHashMap<>();
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if (a.equals("--help") || a.equals("-h")) {
        throw new Refused(usage());
      }
      if (!a.startsWith("--") || i + 1 >= args.length) {
        throw new Refused("Option inconnue ou sans valeur : " + a + "\n" + usage());
      }
      String key = a.substring(2);
      if (!Set.of("size", "api", "mailpit", "workers", "seed").contains(key)) {
        throw new Refused("Option inconnue : " + a + "\n" + usage());
      }
      options.put(key, args[++i]);
    }
    return options;
  }

  private static long parseLong(String value, String option) {
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      throw new Refused(option + " attend un nombre entier : " + value);
    }
  }

  private static String usage() {
    return """
        Usage : java -cp tools/seed/target/classes com.psymate.seed.Seed --size small|medium|demo
                [--api http://localhost:8080] [--mailpit http://localhost:8025]
                [--workers N] [--seed 20261101]""";
  }

  private static String pretty(Duration d) {
    long s = d.toSeconds();
    return s >= 60
        ? "%d min %02d s".formatted(s / 60, s % 60)
        : "%.1f s".formatted(d.toMillis() / 1000.0);
  }
}
