package com.psymate.seed;

import com.psymate.seed.ApiClient.Response;
import com.psymate.seed.Plan.CloseSession;
import com.psymate.seed.Plan.CreateEntry;
import com.psymate.seed.Plan.LinkPlan;
import com.psymate.seed.Plan.PatchEntry;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.Plan.StartSession;
import com.psymate.seed.Plan.Step;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Exécute un {@link PatientPlan} par l'API REST, quel que soit le transport : HTTP réel (le {@link
 * Runner}) ou MockMvc (les tests de performance). Les clés symboliques du plan sont résolues en
 * identifiants au fil de l'eau ; toute réponse inattendue interrompt avec le corps problem+json.
 */
public final class StepExecutor {

  /** Un appel REST : méthode, chemin (avec la requête), corps JSON éventuel. */
  @FunctionalInterface
  public interface Transport {
    Response call(String method, String path, Object body);
  }

  /** Ce qui a été écrit pour un patient. */
  public record Result(
      String patientId,
      int sessions,
      int entries,
      int links,
      int patches,
      Map<String, String> ids) {}

  /** Dossier écrit en partie : le patient existe déjà avec cet identifiant. */
  public static final class IncompleteException extends SeedException {
    private final String patientId;

    IncompleteException(String patientId, RuntimeException cause) {
      super(cause.getMessage(), cause);
      this.patientId = patientId;
    }

    public String patientId() {
      return patientId;
    }
  }

  private StepExecutor() {}

  /**
   * @param onEntry appelé après chaque entrée écrite avec 1 (progression), peut être nul
   */
  public static Result execute(PatientPlan plan, Transport transport, IntConsumer onEntry) {
    Map<String, Object> patient = new LinkedHashMap<>();
    patient.put("displayName", plan.displayName());
    patient.put("firstName", plan.firstName());
    patient.put("lastName", plan.lastName());
    patient.put("birthDate", plan.birthDate());
    patient.put("pronoun", plan.pronoun());
    patient.put("tags", plan.tags());
    Response created = expect(transport, "POST", "/api/patients", patient, 201);
    String patientId = idOf(created);
    String entriesPath = "/api/patients/" + patientId + "/entries";

    try {
      return write(plan, transport, onEntry, patientId, entriesPath);
    } catch (RuntimeException e) {
      throw new IncompleteException(patientId, e);
    }
  }

  private static Result write(
      PatientPlan plan,
      Transport transport,
      IntConsumer onEntry,
      String patientId,
      String entriesPath) {
    Map<String, String> ids = new HashMap<>();
    int sessions = 0;
    int entries = 0;
    int links = 0;
    int patches = 0;
    for (Step step : plan.steps()) {
      switch (step) {
        case StartSession s -> {
          Map<String, Object> occurred = new LinkedHashMap<>();
          occurred.put("from", Body.iso(s.occurredFrom()));
          Map<String, Object> session = new LinkedHashMap<>();
          session.put("modality", s.modality());
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("type", "session");
          body.put("occurred", occurred);
          body.put("session", session);
          ids.put(s.ref(), idOf(expect(transport, "POST", entriesPath, body, 201)));
          sessions++;
        }
        case CloseSession c -> {
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("durationMin", c.durationMin());
          body.put("summary", c.summary());
          expect(
              transport,
              "POST",
              "/api/sessions/" + resolve(ids, c.sessionRef()) + "/close",
              body,
              200);
        }
        case CreateEntry e -> {
          Map<String, Object> body = new LinkedHashMap<>(e.body());
          body.put("sessionId", resolve(ids, e.sessionRef()));
          if (!e.links().isEmpty()) {
            List<Object> resolved = new ArrayList<>();
            for (LinkPlan l : e.links()) {
              Map<String, Object> link = new LinkedHashMap<>();
              link.put("targetId", resolve(ids, l.targetRef()));
              link.put("linkType", l.linkType());
              link.put("direction", l.direction());
              resolved.add(link);
            }
            body.put("links", resolved);
          }
          ids.put(e.ref(), idOf(expect(transport, "POST", entriesPath, body, 201)));
          entries++;
          links += e.links().size();
          if (onEntry != null) {
            onEntry.accept(1);
          }
        }
        case PatchEntry p -> {
          expect(transport, "PATCH", "/api/entries/" + resolve(ids, p.ref()), p.body(), 200);
          patches++;
        }
      }
    }
    return new Result(patientId, sessions, entries, links, patches, Map.copyOf(ids));
  }

  /**
   * Crée les tags manquants du praticien courant (par libellé, sans tenir compte de la casse) : une
   * entrée ou un patient avec un tag inconnu est refusé (422), il faut donc les créer avant.
   */
  public static void ensureTags(Transport transport, Collection<String> labels) {
    Set<String> have = tagLabels(transport);
    for (String label : labels) {
      if (!have.contains(label.toLowerCase(Locale.ROOT))) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("label", label);
        Response r = transport.call("POST", "/api/tags", body);
        // un doublon (409 ou 422) est toléré : la relecture ci-dessous tranche
        if (r.status() != 201 && r.status() != 409 && r.status() != 422) {
          throw new SeedException("Création du tag « " + label + " » : " + ApiClient.describe(r));
        }
      }
    }
    have = tagLabels(transport);
    for (String label : labels) {
      if (!have.contains(label.toLowerCase(Locale.ROOT))) {
        throw new SeedException("Le tag « " + label + " » n'a pas pu être créé.");
      }
    }
  }

  private static Set<String> tagLabels(Transport transport) {
    Set<String> labels = new HashSet<>();
    Response r = expect(transport, "GET", "/api/tags", null, 200);
    if (r.value() instanceof List<?> list) {
      for (Object o : list) {
        if (o instanceof Map<?, ?> m && m.get("label") != null) {
          labels.add(m.get("label").toString().toLowerCase(Locale.ROOT));
        }
      }
    }
    return labels;
  }

  private static String resolve(Map<String, String> ids, String ref) {
    String id = ids.get(ref);
    if (id == null) {
      throw new SeedException("Clé de plan inconnue : " + ref);
    }
    return id;
  }

  private static String idOf(Response response) {
    Object id = response.json().get("id");
    if (id == null) {
      throw new SeedException("Réponse sans identifiant : " + response.body());
    }
    return id.toString();
  }

  private static Response expect(
      Transport transport, String method, String path, Object body, int expected) {
    Response r = transport.call(method, path, body);
    if (r.status() != expected) {
      throw new SeedException(
          method
              + " "
              + path
              + " : attendu "
              + expected
              + ", reçu "
              + ApiClient.describe(r)
              + (body == null ? "" : "\n  corps envoyé : " + abbreviate(Json.write(body))));
    }
    return r;
  }

  private static String abbreviate(String s) {
    return s.length() <= 600 ? s : s.substring(0, 600) + "…";
  }
}
