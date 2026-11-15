package com.psymate.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.seed.AgendaScenario;
import com.psymate.seed.AgendaSeeder;
import com.psymate.seed.ApiClient.Response;
import com.psymate.seed.Json;
import com.psymate.seed.Plan;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.Plan.Practitioner;
import com.psymate.seed.Scenario;
import com.psymate.seed.Seed;
import com.psymate.seed.SeedException;
import com.psymate.seed.StepExecutor;
import com.psymate.seed.StepExecutor.Transport;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * M11-09 : exécute le seed {@code small} (patients puis agenda) par l'API, sur des praticiens neufs
 * (Claire et Karim, chacun dans son cabinet), avec le générateur partagé de {@code tools/seed},
 * puis vérifie en SQL et par {@code GET /api/agenda} les critères de I5 M11-09.
 *
 * <p>Comme les autres tests qui importent {@code com.psymate.seed}, il n'est compilé que sous le
 * profil Maven {@code perf} ; il se lance seul par {@code ./mvnw -B -Pperf verify
 * -Dit.test=SeedAgendaIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class SeedAgendaIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private final Map<Practitioner, UUID> practitioners = new EnumMap<>(Practitioner.class);
  private final Map<Practitioner, RequestPostProcessor> sessions =
      new EnumMap<>(Practitioner.class);

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void smallSeedPlansAnAgendaForEachPractitioner() {
    long seed = Scenario.DEFAULT_SEED;
    Plan plan = Scenario.small(seed);
    assertThat(Scenario.validate(plan)).isEmpty();
    for (Practitioner who : Practitioner.values()) {
      register(who);
    }
    for (Practitioner who : Practitioner.values()) {
      TreeSet<String> tags = new TreeSet<>();
      plan.patients().stream()
          .filter(p -> p.practitioner() == who)
          .forEach(p -> tags.addAll(p.tags()));
      StepExecutor.ensureTags(transport(who), tags);
    }
    for (PatientPlan p : plan.patients()) {
      StepExecutor.execute(p, transport(p.practitioner()), null);
    }

    LocalDate today = LocalDate.now(AgendaScenario.ZONE);
    Map<Practitioner, AgendaSeeder.Result> first = new EnumMap<>(Practitioner.class);
    for (Practitioner who : Practitioner.values()) {
      AgendaSeeder.Result r = AgendaSeeder.seed(transport(who), today, Seed.agendaSeed(seed, who));
      assertThat(r.alreadyPresent()).isFalse();
      assertThat(r.appointments()).isPositive();
      first.put(who, r);
    }
    UUID[] ids = practitioners.values().toArray(UUID[]::new);
    String mine = "practitioner_id in (?, ?)";

    // count(*) > 0 par praticien seedé
    for (Practitioner who : Practitioner.values()) {
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from appointment where practitioner_id = ?",
                  Integer.class,
                  practitioners.get(who)))
          .as("rendez-vous de %s", who)
          .isEqualTo(first.get(who).appointments());
    }
    // au moins une série de 4 occurrences
    assertThat(
            jdbc.queryForObject(
                "select coalesce(max(n), 0) from (select count(*) n from appointment where "
                    + mine
                    + " and series_id is not null group by series_id) s",
                Integer.class,
                (Object[]) ids))
        .isGreaterThanOrEqualTo(4);
    // au moins un cancelled avec cancelled_at, et ck_appointment_cancel partout
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment where "
                    + mine
                    + " and status = 'cancelled' and cancelled_at is not null",
                Integer.class,
                (Object[]) ids))
        .isEqualTo(8 * Practitioner.values().length);
    // une tentative par semaine et par praticien
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment where " + mine + " and status = 'tentative'",
                Integer.class,
                (Object[]) ids))
        .isEqualTo(8 * Practitioner.values().length);
    // aucun rendez-vous hors [now - 5 semaines, now + 5 semaines]
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment where "
                    + mine
                    + " and (starts_at < now() - interval '5 weeks'"
                    + " or ends_at > now() + interval '5 weeks')",
                Integer.class,
                (Object[]) ids))
        .isZero();
    // cabinet et praticien du patient, aucun patient archivé
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment a join patient p on p.id = a.patient_id where "
                    + "a."
                    + mine
                    + " and (a.tenant_id <> p.tenant_id or a.practitioner_id <> p.owner_id"
                    + " or p.archived_at is not null)",
                Integer.class,
                (Object[]) ids))
        .isZero();
    // lundi-samedi, 8 h - 19 h à Paris
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment where "
                    + mine
                    + " and (extract(isodow from starts_at at time zone 'Europe/Paris') = 7"
                    + " or (starts_at at time zone 'Europe/Paris')::time < time '08:00'"
                    + " or (ends_at at time zone 'Europe/Paris')::time > time '19:00')",
                Integer.class,
                (Object[]) ids))
        .isZero();
    // 70 % cabinet, 20 % visio, 10 % téléphone
    Map<String, Object> share =
        jdbc.queryForMap(
            "select avg((modality = 'in_person')::int) as in_person,"
                + " avg((modality = 'video')::int) as video,"
                + " avg((modality = 'phone')::int) as phone from appointment where "
                + mine,
            (Object[]) ids);
    assertThat(((Number) share.get("in_person")).doubleValue()).isBetween(0.65, 0.75);
    assertThat(((Number) share.get("video")).doubleValue()).isBetween(0.15, 0.25);
    assertThat(((Number) share.get("phone")).doubleValue()).isBetween(0.05, 0.15);

    // GET /api/agenda sur la semaine courante du praticien de démonstration : 10 à 16
    LocalDate monday = AgendaScenario.currentMonday(today);
    Response week =
        transport(Practitioner.CLAIRE)
            .call(
                "GET",
                "/api/agenda?from="
                    + monday.atStartOfDay(AgendaScenario.ZONE).toInstant()
                    + "&to="
                    + monday.plusWeeks(1).atStartOfDay(AgendaScenario.ZONE).toInstant(),
                null);
    assertThat(week.status()).isEqualTo(200);
    assertThat((List<?>) week.json().get("appointments")).hasSizeBetween(10, 16);
    assertThat(first.get(Practitioner.CLAIRE).currentWeekActive()).isBetween(10, 16);

    // idempotence : une relance n'écrit rien
    String countMine = "select count(*) from appointment where " + mine;
    int before = jdbc.queryForObject(countMine, Integer.class, (Object[]) ids);
    for (Practitioner who : Practitioner.values()) {
      AgendaSeeder.Result again =
          AgendaSeeder.seed(transport(who), today, Seed.agendaSeed(seed, who));
      assertThat(again.alreadyPresent()).isTrue();
      assertThat(again.requests()).isZero();
    }
    assertThat(jdbc.queryForObject(countMine, Integer.class, (Object[]) ids)).isEqualTo(before);
  }

  // ---- praticiens neufs, chacun dans son cabinet (comme PerfSupport)

  private void register(Practitioner who) {
    UUID tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet seed agenda')", tenant);
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Seed', 'Agenda', 'Dr Seed', 'psychologist')",
        id,
        tenant,
        email,
        email);
    CurrentPractitioner principal =
        new CurrentPractitioner(id, tenant, email, "Dr Seed", "psychologist", "none");
    practitioners.put(who, id);
    sessions.put(
        who,
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())));
  }

  private Transport transport(Practitioner who) {
    RequestPostProcessor me = sessions.get(who);
    return (method, path, body) -> {
      try {
        MockHttpServletRequestBuilder builder =
            switch (method) {
              case "POST" -> MockMvcRequestBuilders.post(path);
              case "PATCH" -> MockMvcRequestBuilders.patch(path);
              case "GET" -> MockMvcRequestBuilders.get(path);
              default -> throw new IllegalArgumentException(method);
            };
        if (body != null) {
          builder
              .contentType(MediaType.APPLICATION_JSON)
              .content(Json.write(body).getBytes(StandardCharsets.UTF_8));
        }
        MvcResult r = mvc.perform(builder.with(me)).andReturn();
        return new Response(
            r.getResponse().getStatus(),
            r.getResponse().getContentAsString(StandardCharsets.UTF_8));
      } catch (Exception e) {
        throw new SeedException(method + " " + path + " : " + e, e);
      }
    };
  }
}
