package com.psymate.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.psymate.directory.service.ContactRequestService;
import com.psymate.identity.api.CurrentPractitioner;
import java.net.URI;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Non-régression de l'isolation par les données (M9-19, revue §5 A-19, mesure 3). Le test écrit,
 * dans un tenant à lui, des données « parasites » de chaque sorte que les autres tests laissent en
 * base partagée : un praticien publié dans l'annuaire, un contact échu, un export échu et une
 * publication d'événement inachevée. Il joue ensuite quatre scénarios représentatifs avant et après
 * ces parasites, avec les mêmes attentes : purge des contacts, exports échus, liste admin, annuaire
 * public. Il vérifie aussi que le contexte pointe bien vers la base partagée de la JVM.
 *
 * <p>Ce que le test prouve : le câblage de la base partagée et le motif d'isolation (marqueur ou
 * tenant propre au test, purges vérifiées sur les lignes du test). Il ne rejoue pas les vrais tests
 * des modules : leur protection vient de {@code SharedDatabaseHygieneTest} (assertions sur toute
 * une table, purges, rejeu d'outbox) et de l'ordre aléatoire des classes, qui fait apparaître une
 * dépendance cachée entre classes.
 *
 * <p>Une sous-classe {@code SharedDatabaseIsolationIT} par tranche d'IT (tools/it-slices.txt) : la
 * base est partagée par JVM, donc chaque tranche a la sienne à vérifier.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
public abstract class SharedDatabaseIsolationScenarios {

  private static final AtomicInteger IP = new AtomicInteger();

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
  @Autowired private ContactRequestService contacts;
  private final JsonMapper json = JsonMapper.builder().build();

  private UUID tenant;
  private UUID parasiteTenant;
  private String parasiteListener;

  @BeforeEach
  void tenants() {
    tenant = tenant("Cabinet isolation");
    parasiteTenant = tenant("Cabinet parasite");
    parasiteListener = "test.isolation." + UUID.randomUUID();
  }

  /**
   * Rien ne reste pour les autres tests : ni ligne échue (B3), ni publication inachevée (B2), ni
   * administrateur actif (TestAdmins), ni profil dans l'annuaire public de la JVM.
   */
  @AfterEach
  void neutraliseParasites() {
    TestAdmins.closeInTenant(jdbc, tenant);
    jdbc.update(
        "update practitioner_profile set published = false where practitioner_id in"
            + " (select id from practitioner where tenant_id in (?, ?))",
        tenant,
        parasiteTenant);
    jdbc.update(
        "update export_job set purged_at = now() where purged_at is null"
            + " and practitioner_id in (select id from practitioner where tenant_id in (?, ?))",
        tenant,
        parasiteTenant);
    jdbc.update(
        "delete from contact_request where purge_after < now() and practitioner_id in"
            + " (select id from practitioner where tenant_id in (?, ?))",
        tenant,
        parasiteTenant);
    jdbc.update("delete from event_publication where listener_id = ?", parasiteListener);
  }

  // ---- données

  private UUID tenant(String name) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, ?)", id, name);
    return id;
  }

  private UUID practitioner(UUID tenantId, String displayName, String role) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, email_confirmed_at,"
            + " password_hash, first_name, last_name, display_name, profession,"
            + " verification_status, role, totp_secret_enc, totp_enabled_at, verified_at)"
            + " values (?, ?, ?, ?, now(), 'x', 'Maya', 'Kaddour', ?, 'psychologist', 'approved',"
            + " ?, ?, ?, now())",
        id,
        tenantId,
        email,
        email,
        displayName,
        role,
        "admin".equals(role) ? new byte[] {1} : null,
        "admin".equals(role) ? Timestamp.from(Instant.now()) : null);
    return id;
  }

  private void publishedProfile(UUID practitioner, String slug, String name) {
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, display_name, published, bio,"
            + " city, modalities, audiences, languages, availability)"
            + " values (?, ?, ?, true, 'Suivi', 'Lyon', '{video}', '{adults}', '{fr}',"
            + " 'accepting')",
        practitioner,
        slug,
        name);
  }

  private UUID contact(UUID practitioner, String createdOffset, String purgeOffset) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into contact_request (id, practitioner_id, sender_name, sender_email, message,"
            + " consent_at, ip_hash, created_at, purge_after) values (?, ?, 'N', 'n@example.test',"
            + " 'm', now(), 'h', now() + ?::interval, now() + ?::interval)",
        id,
        practitioner,
        createdOffset,
        purgeOffset);
    return id;
  }

  /** Export terminé dont le lien expire au décalage donné (négatif : échu, donc à purger). */
  private UUID doneExport(UUID practitioner, String expiresOffset) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into export_job (id, practitioner_id, status, finished_at, storage_key,"
            + " expires_at) values (?, ?, 'done', now() - interval '8 days', ?,"
            + " now() + ?::interval)",
        id,
        practitioner,
        "exports/" + id + ".zip",
        expiresOffset);
    return id;
  }

  /** Les parasites, dans leur tenant : une ligne de chaque sorte que laissent les autres tests. */
  private void insertParasites() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID p = practitioner(parasiteTenant, "Dr Parasite " + tag, "practitioner");
    publishedProfile(p, "parasite-" + tag, "Parasite " + tag);
    contact(p, "-40 days", "-10 days");
    doneExport(p, "-1 day");
    UUID publication = UUID.randomUUID();
    jdbc.update(
        "insert into event_publication (id, listener_id, event_type, serialized_event,"
            + " publication_date, status) values (?, ?, 'com.psymate.events.Parasite', ?, now(),"
            + " 'PUBLISHED')",
        publication,
        parasiteListener,
        "{\"id\":\"" + publication + "\"}");
  }

  // ---- scénarios, chacun sur ses propres données

  private void contactPurgeKeepsOnlyTheRowsNotYetDue() {
    UUID p = practitioner(tenant, "Dr Contact", "practitioner");
    UUID old = contact(p, "-31 days", "-1 day");
    UUID fresh = contact(p, "0 seconds", "30 days");

    int purged = contacts.purgeDue();

    assertThat(purged).isGreaterThanOrEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "select id from contact_request where practitioner_id = ?", UUID.class, p))
        .containsExactly(fresh)
        .doesNotContain(old);
  }

  private void dueExportsAreTheExpiredOnes() {
    UUID p = practitioner(tenant, "Dr Export", "practitioner");
    UUID due = doneExport(p, "-1 minute");
    UUID live = doneExport(p, "6 days");

    // Même sélection que la purge des exports (ExportJobJpaRepository.dueForPurge), lue en SQL :
    // testsupport ne passe pas par le repository d'un module (ArchitectureTests).
    List<UUID> dueIds =
        jdbc.queryForList(
            "select id from export_job where status = 'done' and purged_at is null"
                + " and expires_at < now()",
            UUID.class);

    assertThat(dueIds).contains(due).doesNotContain(live);
  }

  private void adminListShowsOnlyTheMarkedAccounts() throws Exception {
    String m = "Iso" + UUID.randomUUID().toString().substring(0, 8);
    UUID admin = practitioner(tenant, "Admin Isolation", "admin");
    UUID alpha = practitioner(tenant, "Dr " + m + " Alpha", "practitioner");
    UUID beta = practitioner(tenant, "Dr " + m + " Beta", "practitioner");

    JsonNode body =
        json.readTree(
            mvc.perform(get(URI.create("/api/admin/practitioners?q=" + m)).with(asAdmin(admin)))
                .andReturn()
                .getResponse()
                .getContentAsString());

    List<UUID> ids = new ArrayList<>();
    for (JsonNode n : body.path("items")) {
      ids.add(UUID.fromString(n.path("id").asString()));
    }
    assertThat(ids).containsExactlyInAnyOrder(alpha, beta);
    assertThat(body.path("nextCursor").isNull()).isTrue();
  }

  private void publicDirectoryFindsOnlyTheTaggedProfile() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID p = practitioner(tenant, "Pub " + tag, "practitioner");
    publishedProfile(p, "pub-" + tag, "Pub " + tag);

    JsonNode body =
        json.readTree(
            mvc.perform(anon("/api/public/directory?q=Pub " + tag))
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(body.path("total").asInt()).isEqualTo(1);
    assertThat(body.path("items").get(0).path("slug").asString()).isEqualTo("pub-" + tag);
  }

  private void runScenarios() throws Exception {
    contactPurgeKeepsOnlyTheRowsNotYetDue();
    dueExportsAreTheExpiredOnes();
    adminListShowsOnlyTheMarkedAccounts();
    publicDirectoryFindsOnlyTheTaggedProfile();
  }

  // ---- tests

  @Test
  void theContextUsesTheSharedDatabaseOfTheJvm() throws Exception {
    try (Connection c = dataSource.getConnection()) {
      assertThat(c.getMetaData().getURL()).isEqualTo(SharedPostgres.container().getJdbcUrl());
    }
  }

  @Test
  void scenariosGiveTheSameResultsWithForeignDataInTheDatabase() throws Exception {
    runScenarios();

    insertParasites();
    runScenarios();
  }

  // ---- requêtes

  /**
   * Adresse source unique par appel, sous un préfixe propre à cette classe (10.78.x.y) : le seau de
   * débit de l'annuaire est par IP et vit dans le contexte, que cette classe partage avec d'autres
   * (RateLimitIT a 10.77, PublicDirectoryIT 10.9, etc.).
   */
  private static MockHttpServletRequestBuilder anon(String url) {
    int n = IP.incrementAndGet();
    String ip = "10.78." + (n / 250) + "." + (n % 250 + 1);
    return get(url)
        .with(
            r -> {
              r.setRemoteAddr(ip);
              return r;
            });
  }

  private RequestPostProcessor asAdmin(UUID id) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            id, tenant, id + "@example.test", "Admin Isolation", "psychologist", "approved");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }
}
