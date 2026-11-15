package com.psymate.seed;

import com.psymate.seed.AgendaScenario.AppointmentPlan;
import com.psymate.seed.ApiClient.Response;
import com.psymate.seed.StepExecutor.Transport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Écrit l'agenda de démonstration d'un praticien (M11-09) par l'API REST, comme le reste du seed :
 * {@code POST /api/appointments} (avec {@code recurrence} pour une série), puis {@code POST
 * /api/appointments/{id}/cancel} pour le rendez-vous annulé de chaque semaine. L'API pose donc
 * elle-même {@code tenant_id}, {@code practitioner_id}, {@code series_id} et {@code cancelled_at}
 * (contrainte {@code ck_appointment_cancel}) et refuse un patient archivé.
 *
 * <p>Patients : ceux du praticien encore actifs (liste par défaut de {@code GET /api/patients})
 * dont le nom commence par « SEED · » ou « DEMO · », triés par nom. Idempotence : si la fenêtre des
 * 8 semaines contient déjà un rendez-vous (annulé compris) de l'un d'eux, rien n'est écrit.
 */
public final class AgendaSeeder {

  /** Bilan pour un praticien. */
  public record Result(
      boolean alreadyPresent,
      int patients,
      int requests,
      int appointments,
      int series,
      int cancelled,
      int currentWeekActive) {

    public String describe() {
      if (patients == 0) {
        return "aucun patient actif du seed, rien à planifier";
      }
      if (alreadyPresent) {
        return "déjà présent ("
            + currentWeekActive
            + " rendez-vous actifs cette semaine), rien d'écrit";
      }
      return appointments
          + " rendez-vous pour "
          + patients
          + " patients ("
          + series
          + " séries, "
          + cancelled
          + " annulés), "
          + currentWeekActive
          + " actifs cette semaine";
    }
  }

  private AgendaSeeder() {}

  public static Result seed(Transport api, LocalDate today, long seed) {
    List<String> patients = seedPatients(api);
    if (patients.isEmpty()) {
      return new Result(false, 0, 0, 0, 0, 0, 0);
    }
    Set<String> ids = new HashSet<>(patients);
    String window =
        "/api/agenda?from="
            + AgendaScenario.windowStart(today)
            + "&to="
            + AgendaScenario.windowEnd(today)
            + "&includeCancelled=true";
    for (Object o : appointments(expect(api, "GET", window, null, 200))) {
      if (o instanceof Map<?, ?> a
          && a.get("patient") instanceof Map<?, ?> p
          && ids.contains(String.valueOf(p.get("id")))) {
        return new Result(true, patients.size(), 0, 0, 0, 0, currentWeekActive(api, today));
      }
    }

    int requests = 0;
    int appointments = 0;
    int series = 0;
    int cancelled = 0;
    for (AppointmentPlan plan : AgendaScenario.generate(patients.size(), today, seed)) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("patientId", patients.get(plan.patient()));
      body.put("startsAt", plan.startsAt().toString());
      body.put("durationMin", plan.durationMin());
      body.put("modality", plan.modality());
      if (plan.planningNote() != null) {
        body.put("planningNote", plan.planningNote());
      }
      body.put("tentative", plan.tentative());
      if (plan.series()) {
        Map<String, Object> recurrence = new LinkedHashMap<>();
        recurrence.put("everyWeeks", 1);
        recurrence.put("count", plan.weeklyOccurrences());
        body.put("recurrence", recurrence);
      }
      body.put("tz", AgendaScenario.ZONE.getId());
      List<Object> created = appointments(expect(api, "POST", "/api/appointments", body, 201));
      requests++;
      if (created.size() != plan.weeklyOccurrences()) {
        throw new SeedException(
            "POST /api/appointments : "
                + created.size()
                + " rendez-vous créés, "
                + plan.weeklyOccurrences()
                + " attendus");
      }
      appointments += created.size();
      if (plan.series()) {
        series++;
      }
      if (plan.cancelled()) {
        String id = String.valueOf(((Map<?, ?>) created.get(0)).get("id"));
        Map<String, Object> cancel = new LinkedHashMap<>();
        cancel.put("reason", plan.cancelReason());
        expect(api, "POST", "/api/appointments/" + id + "/cancel", cancel, 200);
        requests++;
        cancelled++;
      }
    }
    return new Result(
        false,
        patients.size(),
        requests,
        appointments,
        series,
        cancelled,
        currentWeekActive(api, today));
  }

  /** Rendez-vous actifs de la semaine courante (lundi 0 h – lundi suivant 0 h, Paris). */
  public static int currentWeekActive(Transport api, LocalDate today) {
    LocalDate monday = AgendaScenario.currentMonday(today);
    String path =
        "/api/agenda?from="
            + monday.atStartOfDay(AgendaScenario.ZONE).toInstant()
            + "&to="
            + monday.plusWeeks(1).atStartOfDay(AgendaScenario.ZONE).toInstant();
    return appointments(expect(api, "GET", path, null, 200)).size();
  }

  /** Identifiants des patients actifs du seed, triés par nom d'affichage (ordre stable). */
  static List<String> seedPatients(Transport api) {
    Map<String, String> byName = new TreeMap<>();
    String cursor = null;
    do {
      String path =
          "/api/patients?limit=100" + (cursor == null ? "" : "&cursor=" + Runner.encode(cursor));
      Map<String, Object> page = expect(api, "GET", path, null, 200).json();
      if (page.get("items") instanceof List<?> items) {
        for (Object o : items) {
          if (o instanceof Map<?, ?> m
              && m.get("displayName") instanceof String name
              && (name.startsWith("SEED · ") || name.startsWith("DEMO · "))
              && m.get("archivedAt") == null) {
            byName.put(name + " " + m.get("id"), m.get("id").toString());
          }
        }
      }
      cursor = page.get("nextCursor") == null ? null : page.get("nextCursor").toString();
    } while (cursor != null);
    return new ArrayList<>(byName.values());
  }

  private static List<Object> appointments(Response response) {
    Object list = response.json().get("appointments");
    if (list instanceof List<?> l) {
      return new ArrayList<>(l);
    }
    throw new SeedException("Réponse sans liste appointments : " + response.body());
  }

  private static Response expect(
      Transport api, String method, String path, Object body, int status) {
    Response r = api.call(method, path, body);
    if (r.status() != status) {
      throw new ApiClient.ApiException(method, path, r);
    }
    return r;
  }
}
