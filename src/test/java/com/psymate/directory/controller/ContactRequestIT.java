package com.psymate.directory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.directory.ContactMailpit;
import com.psymate.directory.ContactTestConfig;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContactTestConfig.class)
class ContactRequestIT {

  private static final AtomicInteger IP = new AtomicInteger();
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private GenericContainer<?> mailpit;

  private ContactMailpit mails;
  private UUID tenant;

  @BeforeEach
  void setUp() {
    mails = new ContactMailpit(mailpit);
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  /** Chaque test vient d'une IP distincte pour ne pas partager le seau 5 / h. */
  private static String freshIp() {
    int n = IP.incrementAndGet();
    return "10.7." + (n / 250) + "." + (n % 250 + 1);
  }

  private record Practitioner(UUID id, String accountEmail, String slug) {}

  /** Praticien avec fiche ; contactEmail null = pas d'adresse de contact sur la fiche. */
  private Practitioner practitioner(boolean published, String contactEmail) {
    UUID id = UUID.randomUUID();
    String tag = id.toString().substring(0, 8);
    String account = "compte-" + tag + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'T', 'P', ?, 'psychologist', 'approved')",
        id,
        tenant,
        account,
        account,
        "Praticien " + tag);
    String slug = "contact-" + tag;
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, display_name, published,"
            + " contact_email) values (?, ?, ?, ?, ?)",
        id,
        slug,
        "Praticien " + tag,
        published,
        contactEmail);
    return new Practitioner(id, account, slug);
  }

  private static Map<String, Object> body(String visitorEmail) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", "Camille Visiteur");
    m.put("email", visitorEmail);
    m.put("subject", "Première consultation");
    m.put("message", "Bonjour, je souhaite prendre rendez-vous.");
    m.put("consent", true);
    m.put("website", "");
    m.put("formShownAt", Instant.now().minusSeconds(30).toString());
    return m;
  }

  private ResultActions send(String slug, String ip, Map<String, Object> body) throws Exception {
    return sendTo(URI.create("/api/public/directory/practitioners/" + slug + "/contact"), ip, body);
  }

  /** URI brute (non ré-encodée) : permet de tester un chemin encodé comme {@code /%63ontact}. */
  private ResultActions sendTo(URI uri, String ip, Map<String, Object> body) throws Exception {
    return mvc.perform(
        post(uri)
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(body)));
  }

  private int rows(UUID practitionerId) {
    return jdbc.queryForObject(
        "select count(*) from contact_request where practitioner_id = ?",
        Integer.class,
        practitionerId);
  }

  /** Le relais est asynchrone : attend relayed_at ou relay_error. */
  private Map<String, Object> awaitRelayed(UUID practitionerId) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (true) {
      Map<String, Object> row =
          jdbc.queryForMap(
              "select * from contact_request where practitioner_id = ?", practitionerId);
      if (row.get("relayed_at") != null || row.get("relay_error") != null) {
        return row;
      }
      assertThat(System.nanoTime()).as("relais non terminé").isLessThan(deadline);
      Thread.sleep(100);
    }
  }

  @Test
  void validMessageIsStoredRelayedToContactEmailWithReplyTo() throws Exception {
    String contact = "cabinet-" + UUID.randomUUID() + "@example.test";
    Practitioner p = practitioner(true, contact);
    String visitor = "visiteur-" + UUID.randomUUID() + "@example.test";
    String ip = freshIp();

    send(p.slug(), ip, body(visitor))
        .andExpect(status().isAccepted())
        .andExpect(content().string(""))
        .andExpect(header().string("Cache-Control", "no-store"));

    Map<String, Object> row = awaitRelayed(p.id());
    assertThat(row.get("relay_error")).isNull();
    assertThat(row.get("relayed_at")).isNotNull();
    assertThat(row.get("sender_email")).isEqualTo(visitor);
    assertThat(row.get("consent_at")).isNotNull();
    String ipHash = row.get("ip_hash").toString();
    assertThat(ipHash).matches("[0-9a-f]{64}").doesNotContain(ip);
    Long days =
        jdbc.queryForObject(
            "select extract(day from purge_after - created_at)::bigint from contact_request"
                + " where practitioner_id = ?",
            Long.class,
            p.id());
    assertThat(days).isEqualTo(30);

    JsonNode mail = mails.awaitMessage(contact);
    assertThat(mail).as("e-mail reçu dans Mailpit").isNotNull();
    assertThat(mail.path("To").get(0).path("Address").asString()).isEqualTo(contact);
    assertThat(mail.path("ReplyTo").get(0).path("Address").asString()).isEqualTo(visitor);
    assertThat(mail.path("From").path("Address").asString()).isEqualTo("test@gmail.com");
    assertThat(mail.path("Subject").asString()).contains("PsyMate").contains("Camille Visiteur");
    assertThat(mail.path("Text").asString())
        .contains("Camille Visiteur")
        .contains(visitor)
        .contains("Première consultation")
        .contains("Bonjour, je souhaite prendre rendez-vous.")
        .doesNotContain(ip)
        .doesNotContain(ipHash);
  }

  @Test
  void withoutContactEmailTheAccountEmailReceivesTheMessage() throws Exception {
    Practitioner p = practitioner(true, null);
    String visitor = "visiteur-" + UUID.randomUUID() + "@example.test";

    send(p.slug(), freshIp(), body(visitor)).andExpect(status().isAccepted());

    assertThat(awaitRelayed(p.id()).get("relayed_at")).isNotNull();
    JsonNode mail = mails.awaitMessage(p.accountEmail());
    assertThat(mail).isNotNull();
    assertThat(mail.path("ReplyTo").get(0).path("Address").asString()).isEqualTo(visitor);
  }

  @Test
  void headerInjectionInNameIsNeutralised() throws Exception {
    String contact = "cabinet-" + UUID.randomUUID() + "@example.test";
    Practitioner p = practitioner(true, contact);
    Map<String, Object> b = body("v-" + UUID.randomUUID() + "@example.test");
    b.put("name", "Pirate\r\nBcc: autre@example.test");

    send(p.slug(), freshIp(), b).andExpect(status().isAccepted());

    JsonNode mail = mails.awaitMessage(contact);
    assertThat(mail).isNotNull();
    assertThat(mail.path("Bcc").size()).isZero();
    assertThat(mail.path("Subject").asString()).doesNotContain("\n");
  }

  @Test
  void filledHoneypotIsSilentlyIgnored() throws Exception {
    String contact = "cabinet-" + UUID.randomUUID() + "@example.test";
    Practitioner p = practitioner(true, contact);
    Map<String, Object> b = body("bot@example.test");
    b.put("website", "http://spam.example.test");

    send(p.slug(), freshIp(), b).andExpect(status().isAccepted()).andExpect(content().string(""));

    assertThat(rows(p.id())).isZero();
    assertThat(mails.count(contact)).isZero();
  }

  @Test
  void tooRecentOrFutureFormShownAtIsSilentlyIgnored() throws Exception {
    String contact = "cabinet-" + UUID.randomUUID() + "@example.test";
    Practitioner p = practitioner(true, contact);
    for (Instant shown : List.of(Instant.now().minusMillis(500), Instant.now().plusSeconds(3600))) {
      Map<String, Object> b = body("v@example.test");
      b.put("formShownAt", shown.toString());
      send(p.slug(), freshIp(), b).andExpect(status().isAccepted());
    }
    assertThat(rows(p.id())).isZero();
    assertThat(mails.count(contact)).isZero();
  }

  @Test
  void unpublishedOrUnknownProfileIsSilentlyIgnored() throws Exception {
    String contact = "cabinet-" + UUID.randomUUID() + "@example.test";
    Practitioner hidden = practitioner(false, contact);
    String visitor = "inconnu-" + UUID.randomUUID() + "@example.test";

    send(hidden.slug(), freshIp(), body(visitor))
        .andExpect(status().isAccepted())
        .andExpect(content().string(""));
    send("slug-inconnu-" + UUID.randomUUID(), freshIp(), body(visitor))
        .andExpect(status().isAccepted());

    assertThat(rows(hidden.id())).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from contact_request where sender_email = ?",
                Integer.class,
                visitor))
        .isZero();
    assertThat(mails.count(contact)).isZero();
  }

  @Test
  void sixthMessageInTheHourFromTheSameIpIsRateLimited() throws Exception {
    Practitioner p = practitioner(true, "cabinet-" + UUID.randomUUID() + "@example.test");
    String ip = freshIp();
    for (int i = 0; i < 5; i++) {
      send(p.slug(), ip, body("v" + i + "@example.test")).andExpect(status().isAccepted());
    }
    send(p.slug(), ip, body("v6@example.test"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(
            header()
                .string(
                    "Content-Type", org.hamcrest.Matchers.startsWith("application/problem+json")))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    // Une autre IP n'est pas concernée.
    send(p.slug(), freshIp(), body("autre@example.test")).andExpect(status().isAccepted());
    assertThat(rows(p.id())).isEqualTo(6);
  }

  /** Le seau 5 / h se fonde sur le chemin décodé : l'encodage d'URL ne le contourne pas. */
  @Test
  void urlEncodedPathStillCountsTowardsTheContactLimit() throws Exception {
    Practitioner p = practitioner(true, "cabinet-" + UUID.randomUUID() + "@example.test");
    String ip = freshIp();
    URI encoded = URI.create("/api/public/directory/practitioners/" + p.slug() + "/%63ontact");
    for (int i = 0; i < 5; i++) {
      sendTo(encoded, ip, body("v" + i + "@example.test")).andExpect(status().isAccepted());
    }
    sendTo(encoded, ip, body("v6@example.test")).andExpect(status().isTooManyRequests());
    send(p.slug(), ip, body("v7@example.test")).andExpect(status().isTooManyRequests());
    assertThat(rows(p.id())).isEqualTo(5);
  }

  /** Les deux seaux (lectures 60 / min, contact 5 / h) sont indépendants. */
  @Test
  void contactBucketIsIndependentFromTheReadBucket() throws Exception {
    Practitioner p = practitioner(true, "cabinet-" + UUID.randomUUID() + "@example.test");
    String ip = freshIp();
    // Des lectures en nombre ne consomment rien du seau contact...
    for (int i = 0; i < 20; i++) {
      read(p.slug(), ip).andExpect(status().isOk());
    }
    for (int i = 0; i < 5; i++) {
      send(p.slug(), ip, body("v" + i + "@example.test")).andExpect(status().isAccepted());
    }
    // ... et un seau contact épuisé laisse les lectures passer.
    send(p.slug(), ip, body("v6@example.test")).andExpect(status().isTooManyRequests());
    read(p.slug(), ip).andExpect(status().isOk());
  }

  private ResultActions read(String slug, String ip) throws Exception {
    return mvc.perform(
        get("/api/public/directory/practitioners/" + slug)
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                }));
  }

  @Test
  void invalidFieldsAreRejectedWith422AndFieldErrors() throws Exception {
    Practitioner p = practitioner(true, "cabinet-" + UUID.randomUUID() + "@example.test");

    Map<String, Object> noConsent = body("v@example.test");
    noConsent.put("consent", false);
    send(p.slug(), freshIp(), noConsent)
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            header()
                .string(
                    "Content-Type", org.hamcrest.Matchers.startsWith("application/problem+json")))
        .andExpect(jsonPath("$.errors[?(@.field == 'consent')]").isNotEmpty());

    send(p.slug(), freshIp(), body("pas-un-email"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[?(@.field == 'email')]").isNotEmpty());

    Map<String, Object> tooLong = body("v@example.test");
    tooLong.put("message", "x".repeat(4001));
    send(p.slug(), freshIp(), tooLong)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[?(@.field == 'message')]").isNotEmpty());

    Map<String, Object> longName = body("v@example.test");
    longName.put("name", "n".repeat(201));
    longName.put("subject", "s".repeat(201));
    send(p.slug(), freshIp(), longName)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty())
        .andExpect(jsonPath("$.errors[?(@.field == 'subject')]").isNotEmpty());

    send(p.slug(), freshIp(), Map.of())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty())
        .andExpect(jsonPath("$.errors[?(@.field == 'email')]").isNotEmpty())
        .andExpect(jsonPath("$.errors[?(@.field == 'message')]").isNotEmpty())
        .andExpect(jsonPath("$.errors[?(@.field == 'consent')]").isNotEmpty())
        .andExpect(jsonPath("$.errors[?(@.field == 'formShownAt')]").isNotEmpty());

    assertThat(rows(p.id())).isZero();
  }
}
