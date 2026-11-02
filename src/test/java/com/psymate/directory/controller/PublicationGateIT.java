package com.psymate.directory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.directory.DirectoryTestConfig;
import com.psymate.directory.api.ProfileUnpublished;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerRejected;
import com.psymate.identity.api.PractitionerSuspended;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M5-06 : la publication de l'annuaire exige un dossier approuvé (409 avec missing contenant
 * "verification"), et un refus de dossier retire automatiquement le profil de l'annuaire.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({DirectoryTestConfig.class, PublicationGateIT.UnpublishedSink.class})
class PublicationGateIT {

  private static final AtomicInteger IP = new AtomicInteger();

  /**
   * Aucun abonné de production n'écoute ProfileUnpublished : sans abonné, Modulith n'enregistre pas
   * la publication. Cet abonné de test la rend visible dans event_publication.
   */
  static class UnpublishedListener {
    @ApplicationModuleListener(id = "test.profile-unpublished")
    void on(ProfileUnpublished event) {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class UnpublishedSink {
    @Bean
    UnpublishedListener unpublishedListener() {
      return new UnpublishedListener();
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DomainEventPublisher publisher;
  @Autowired private TransactionTemplate tx;

  private UUID tenant;

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  private static String freshIp() {
    int n = IP.incrementAndGet();
    return "10.8." + (n / 250) + "." + (n % 250 + 1);
  }

  private MockHttpServletRequestBuilder anon(String url) {
    return get(url)
        .with(
            r -> {
              r.setRemoteAddr(freshIp());
              return r;
            });
  }

  private UUID practitioner(String status) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Gate', 'psychologist', ?)",
        id,
        tenant,
        email,
        email,
        status);
    return id;
  }

  private RequestPostProcessor as(UUID id) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            id, tenant, id + "@example.test", "Dr Gate", "psychologist", "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private void putProfile(RequestPostProcessor auth, String json) throws Exception {
    mvc.perform(
            put("/api/me/profile").contentType(MediaType.APPLICATION_JSON).content(json).with(auth))
        .andExpect(status().isOk());
  }

  private static final String COMPLETE =
      "{\"displayName\":\"Dr Gate\",\"city\":\"Lyon\",\"contactEmail\":\"a@b.fr\","
          + "\"showEmail\":true}";

  private String slugOf(UUID id) {
    return jdbc.queryForObject(
        "select slug from practitioner_profile where practitioner_id = ?", String.class, id);
  }

  private int unpublishedEvents(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from event_publication where event_type like '%ProfileUnpublished%'"
            + " and listener_id = 'test.profile-unpublished' and serialized_event like ?",
        Integer.class, "%" + id + "%");
  }

  private void publishRejected(UUID id) {
    tx.executeWithoutResult(
        s -> publisher.publish(new PractitionerRejected(tenant, id, "psychologist")));
  }

  private int completedRejections(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from event_publication"
            + " where listener_id = 'directory.practitioner-rejected'"
            + " and completion_date is not null and serialized_event like ?",
        Integer.class,
        "%" + id + "%");
  }

  @Test
  void completeProfileOfUnverifiedPractitionerIsRefusedWithVerificationOnly() throws Exception {
    UUID id = practitioner("under_review");
    RequestPostProcessor auth = as(id);
    putProfile(auth, COMPLETE);
    mvc.perform(post("/api/me/profile/publish").with(auth))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.missing.length()").value(1))
        .andExpect(jsonPath("$.missing[0]").value("verification"));
    assertThat(
            jdbc.queryForObject(
                "select published from practitioner_profile where practitioner_id = ?",
                Boolean.class,
                id))
        .isFalse();
  }

  @Test
  void incompleteAndUnverifiedProfileListsVerificationFirstThenOtherGaps() throws Exception {
    UUID id = practitioner("docs_pending");
    RequestPostProcessor auth = as(id);
    putProfile(auth, "{\"displayName\":\"Dr Gate\"}");
    mvc.perform(post("/api/me/profile/publish").with(auth))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.missing[0]").value("verification"))
        .andExpect(jsonPath("$.missing[1]").value("city"))
        .andExpect(jsonPath("$.missing[2]").value("contact"))
        .andExpect(jsonPath("$.missing.length()").value(3));
  }

  @Test
  void approvedPractitionerPublishesAndAppearsVerifiedInDirectory() throws Exception {
    UUID id = practitioner("approved");
    RequestPostProcessor auth = as(id);
    putProfile(auth, COMPLETE);
    mvc.perform(post("/api/me/profile/publish").with(auth)).andExpect(status().isNoContent());
    String slug = slugOf(id);

    mvc.perform(anon("/api/public/directory/practitioners/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(true));
    mvc.perform(anon("/api/public/directory?city=Lyon&q=Gate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.slug == '" + slug + "')].verified").value(true));
  }

  @Test
  void rejectedPractitionerIsUnpublishedOnceAndCannotRepublish() throws Exception {
    UUID id = practitioner("approved");
    RequestPostProcessor auth = as(id);
    putProfile(auth, COMPLETE);
    mvc.perform(post("/api/me/profile/publish").with(auth)).andExpect(status().isNoContent());
    String slug = slugOf(id);
    mvc.perform(anon("/api/public/directory/practitioners/" + slug)).andExpect(status().isOk());

    jdbc.update("update practitioner set verification_status = 'rejected' where id = ?", id);
    publishRejected(id);

    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () ->
                mvc.perform(anon("/api/public/directory/practitioners/" + slug))
                    .andExpect(status().isNotFound()));
    await().atMost(Duration.ofSeconds(60)).until(() -> unpublishedEvents(id) == 1);
    assertThat(
            jdbc.queryForObject(
                "select published_at is not null from practitioner_profile"
                    + " where practitioner_id = ?",
                Boolean.class,
                id))
        .isTrue();

    mvc.perform(post("/api/me/profile/publish").with(auth))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.missing.length()").value(1))
        .andExpect(jsonPath("$.missing[0]").value("verification"));
  }

  @Test
  void replayedRejectionOnAlreadyUnpublishedProfileEmitsNothingMore() throws Exception {
    UUID id = practitioner("approved");
    RequestPostProcessor auth = as(id);
    putProfile(auth, COMPLETE);
    mvc.perform(post("/api/me/profile/publish").with(auth)).andExpect(status().isNoContent());

    publishRejected(id);
    await().atMost(Duration.ofSeconds(60)).until(() -> unpublishedEvents(id) == 1);
    await().atMost(Duration.ofSeconds(60)).until(() -> completedRejections(id) == 1);

    publishRejected(id);
    await().atMost(Duration.ofSeconds(60)).until(() -> completedRejections(id) == 2);

    assertThat(unpublishedEvents(id)).isEqualTo(1);
  }

  @Test
  void rejectionOfPractitionerWithoutProfileIsHarmless() {
    UUID id = practitioner("rejected");
    publishRejected(id);
    await().atMost(Duration.ofSeconds(60)).until(() -> completedRejections(id) == 1);
    assertThat(unpublishedEvents(id)).isZero();
  }

  /** M1-14b : la suspension manuelle dépublie la fiche comme un refus. */
  @Test
  void suspendedPractitionerIsUnpublished() throws Exception {
    UUID id = practitioner("approved");
    RequestPostProcessor auth = as(id);
    putProfile(auth, COMPLETE);
    mvc.perform(post("/api/me/profile/publish").with(auth)).andExpect(status().isNoContent());
    String slug = slugOf(id);
    mvc.perform(anon("/api/public/directory/practitioners/" + slug)).andExpect(status().isOk());

    jdbc.update("update practitioner set verification_status = 'suspended' where id = ?", id);
    tx.executeWithoutResult(
        s -> publisher.publish(new PractitionerSuspended(tenant, id, "psychologist")));

    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () ->
                mvc.perform(anon("/api/public/directory/practitioners/" + slug))
                    .andExpect(status().isNotFound()));
    await().atMost(Duration.ofSeconds(60)).until(() -> unpublishedEvents(id) == 1);
    mvc.perform(post("/api/me/profile/publish").with(auth))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.missing[0]").value("verification"));
  }
}
