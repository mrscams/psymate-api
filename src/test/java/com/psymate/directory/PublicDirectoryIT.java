package com.psymate.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DirectoryTestConfig.class)
class PublicDirectoryIT {

  private static final AtomicInteger IP = new AtomicInteger();

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  /** Chaque appel de test vient d'une IP distincte pour ne pas partager le seau de débit. */
  private static MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder b) {
    return b.with(
        r -> {
          r.setRemoteAddr(ip);
          return r;
        });
  }

  private static String freshIp() {
    int n = IP.incrementAndGet();
    return "10.9." + (n / 250) + "." + (n % 250 + 1);
  }

  private MockHttpServletRequestBuilder anon(String url) {
    return from(freshIp(), get(url));
  }

  private UUID practitioner(String name, String status, String professionalNumber) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status,"
            + " professional_number) values (?, ?, ?, ?, 'x', 'T', 'P', ?, 'psychologist', ?, ?)",
        id,
        tenant,
        email,
        email,
        name,
        status,
        professionalNumber);
    return id;
  }

  private void profile(
      UUID id,
      String slug,
      String name,
      boolean published,
      String bio,
      String city,
      String extraSql) {
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, display_name, published, bio,"
            + " city, modalities, audiences, languages, availability, contact_phone,"
            + " contact_email, address) values (?, ?, ?, ?, ?, ?, '{video}', '{adults}', '{fr}',"
            + " 'accepting', '0612345678', 'secret@example.test', '9 rue Cachee')",
        id,
        slug,
        name,
        published,
        bio,
        city);
    if (extraSql != null) {
      jdbc.update(extraSql + " where practitioner_id = ?", id);
    }
  }

  @Test
  void anonymousListAndProfileReturn200_unpublishedHidden() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID pub = practitioner("Pub " + tag, "approved", null);
    UUID hidden = practitioner("Hid " + tag, "approved", null);
    profile(pub, "pub-" + tag, "Pub " + tag, true, "Suivi", "Lyon", null);
    profile(hidden, "hid-" + tag, "Hid " + tag, false, "Suivi", "Lyon", null);

    mvc.perform(anon("/api/public/directory?q=Pub " + tag))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].slug").value("pub-" + tag))
        .andExpect(jsonPath("$.items[0].verified").value(true))
        .andExpect(jsonPath("$.items[0].profession").value("psychologist"));
    mvc.perform(anon("/api/public/directory?q=Hid " + tag))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.slug == 'hid-" + tag + "')]").isEmpty());
    mvc.perform(anon("/api/public/directory/practitioners/pub-" + tag))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Pub " + tag))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=60")))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Cache-Control", org.hamcrest.Matchers.containsString("public")));
    mvc.perform(anon("/api/public/directory/practitioners/hid-" + tag))
        .andExpect(status().isNotFound());
    mvc.perform(anon("/api/public/directory/practitioners/inconnu-" + tag))
        .andExpect(status().isNotFound());
  }

  @Test
  void unapprovedPractitionerIsNotVerified() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID id = practitioner("Pending " + tag, "docs_pending", null);
    profile(id, "pending-" + tag, "Pending " + tag, true, null, "Nantes", null);
    mvc.perform(anon("/api/public/directory/practitioners/pending-" + tag))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(false));
  }

  @Test
  void hiddenContactsNeverAppearInRawJson() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID id = practitioner("Discret " + tag, "approved", "RPPS-99887766");
    profile(id, "discret-" + tag, "Discret " + tag, true, "Bio", "Lyon", null);
    String body =
        mvc.perform(anon("/api/public/directory/practitioners/discret-" + tag))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body)
        .doesNotContain("0612345678", "secret@example.test", "9 rue Cachee", "RPPS-99887766");
    String list =
        mvc.perform(anon("/api/public/directory?q=Discret " + tag))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(list)
        .doesNotContain("0612345678", "secret@example.test", "9 rue Cachee", "RPPS-99887766");
  }

  @Test
  void optedInContactsAppear() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID id = practitioner("Ouvert " + tag, "approved", "RPPS-5551");
    profile(
        id,
        "ouvert-" + tag,
        "Ouvert " + tag,
        true,
        "Bio",
        "Lyon",
        "update practitioner_profile set show_phone = true, show_email = true,"
            + " show_address = true, show_professional_number = true");
    mvc.perform(anon("/api/public/directory/practitioners/ouvert-" + tag))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value("0612345678"))
        .andExpect(jsonPath("$.email").value("secret@example.test"))
        .andExpect(jsonPath("$.address").value("9 rue Cachee"))
        .andExpect(jsonPath("$.professionalNumber").value("RPPS-5551"));
  }

  @Test
  void accentInsensitiveSearchFindsBio() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID id = practitioner("Zeta " + tag, "approved", null);
    profile(
        id,
        "zeta-" + tag,
        "Zeta " + tag,
        true,
        "Prise en charge de l'anxiété " + tag,
        "Lyon",
        null);
    mvc.perform(anon("/api/public/directory?q=anxiete " + tag))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.slug == 'zeta-" + tag + "')]").exists());
  }

  @Test
  void trigramFallbackOnDisplayName() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 6);
    UUID id = practitioner("Bouchardon", "approved", null);
    profile(id, "bouchardon-" + tag, "Bouchardon", true, null, "Lyon", null);
    mvc.perform(anon("/api/public/directory?q=Bouchardonn"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.slug == 'bouchardon-" + tag + "')]").exists());
  }

  @Test
  void cityFilterFacetsAndPaging() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    String city = "Ville" + tag;
    for (int i = 0; i < 22; i++) {
      UUID id = practitioner("Praticien" + i, "approved", null);
      profile(id, "p" + i + "-" + tag, "Praticien" + i, true, "x", city, null);
    }
    mvc.perform(anon("/api/public/directory?city=" + city.toUpperCase()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(22))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.items.length()").value(20))
        .andExpect(jsonPath("$.facets.modalities[0].code").value("video"))
        .andExpect(jsonPath("$.facets.modalities[0].count").value(22))
        .andExpect(jsonPath("$.facets.professions[0].code").value("psychologist"))
        .andExpect(jsonPath("$.facets.audiences[0].code").value("adults"))
        .andExpect(jsonPath("$.facets.languages[0].code").value("fr"));
    mvc.perform(anon("/api/public/directory?city=" + city + "&page=2&sort=name"))
        .andExpect(jsonPath("$.items.length()").value(2));
    mvc.perform(anon("/api/public/directory?city=" + city + "&professions=coach"))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(anon("/api/public/directory?city=" + city + "&modalities=home"))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(anon("/api/public/directory?sort=bogus")).andExpect(status().isBadRequest());
  }

  @Test
  void radiusAndDistanceSort() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID near = practitioner("Proche", "approved", null);
    UUID far = practitioner("Loin", "approved", null);
    profile(
        near,
        "proche-" + tag,
        "Proche " + tag,
        true,
        "x",
        "Lyon",
        "update practitioner_profile set geo = point(4.84, 45.76)");
    profile(
        far,
        "loin-" + tag,
        "Loin " + tag,
        true,
        "x",
        "Paris",
        "update practitioner_profile set geo = point(2.35, 48.85)");
    // Depuis Villeurbanne : Lyon à quelques km, Paris à ~390 km.
    mvc.perform(anon("/api/public/directory?lat=45.77&lng=4.88&radiusKm=50&q=" + tag))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].slug").value("proche-" + tag))
        .andExpect(jsonPath("$.items[0].distanceKm").isNumber());
    mvc.perform(
            anon("/api/public/directory?lat=45.77&lng=4.88&radiusKm=1000&sort=distance&q=" + tag))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items[0].slug").value("proche-" + tag))
        .andExpect(jsonPath("$.items[1].slug").value("loin-" + tag));
  }

  @Test
  void sixtyFirstRequestFromSameIpIsRateLimited() throws Exception {
    String ip = freshIp();
    for (int i = 0; i < 60; i++) {
      mvc.perform(from(ip, get("/api/public/directory/practitioners/absent")))
          .andExpect(status().isNotFound());
    }
    mvc.perform(from(ip, get("/api/public/directory")))
        .andExpect(status().isTooManyRequests())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("Retry-After"))
        .andExpect(jsonPath("$.status").value(429));
    // Une autre adresse n'est pas touchée.
    mvc.perform(anon("/api/public/directory")).andExpect(status().isOk());
  }

  @Test
  void filtersNameSortAndAccepting() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    String city = "Filtre" + tag;
    UUID b = practitioner("Bravo", "approved", null);
    UUID a = practitioner("Alpha", "approved", null);
    UUID c = practitioner("Charlie", "approved", null);
    jdbc.update("update practitioner set profession = 'coach' where id = ?", c);
    profile(b, "b-" + tag, "Bravo", true, "x", city, null);
    profile(
        a,
        "a-" + tag,
        "Alpha",
        true,
        "x",
        city,
        "update practitioner_profile set modalities = '{in_person}', audiences = '{children}',"
            + " languages = '{en}', availability = 'closed'");
    profile(c, "c-" + tag, "Charlie", true, "x", city, null);
    String base = "/api/public/directory?city=" + city;
    mvc.perform(anon(base + "&sort=name"))
        .andExpect(jsonPath("$.items[0].displayName").value("Alpha"))
        .andExpect(jsonPath("$.items[1].displayName").value("Bravo"))
        .andExpect(jsonPath("$.items[2].displayName").value("Charlie"));
    mvc.perform(anon(base + "&professions=coach"))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].slug").value("c-" + tag));
    mvc.perform(anon(base + "&modalities=in_person"))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].slug").value("a-" + tag));
    mvc.perform(anon(base + "&audiences=children")).andExpect(jsonPath("$.total").value(1));
    mvc.perform(anon(base + "&languages=en")).andExpect(jsonPath("$.total").value(1));
    mvc.perform(anon(base + "&accepting=true")).andExpect(jsonPath("$.total").value(2));
  }

  @Test
  void invalidParametersGiveProblemJson400() throws Exception {
    for (String qs :
        new String[] {
          "lat=45",
          "sort=bogus",
          "q=" + "x".repeat(101),
          "lat=95&lng=4",
          "lat=45&lng=200",
          "radiusKm=0",
          "radiusKm=1001",
          "page=0",
          "page=abc",
          "lat=abc&lng=4",
          "languages=" + String.join(",", java.util.Collections.nCopies(21, "fr"))
        }) {
      mvc.perform(anon("/api/public/directory?" + qs))
          .andExpect(status().isBadRequest())
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                  .contentTypeCompatibleWith("application/problem+json"))
          .andExpect(jsonPath("$.status").value(400));
    }
  }

  @Test
  void publicRoutesSetNoCookieAndCacheHeaders() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID id = practitioner("Cache " + tag, "approved", null);
    profile(id, "cache-" + tag, "Cache " + tag, true, null, "Lyon", null);
    var ok =
        mvc.perform(anon("/api/public/directory/practitioners/cache-" + tag))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(ok.getHeader("Set-Cookie")).isNull();
    assertThat(ok.getHeader("Cache-Control")).contains("public").contains("max-age=60");
    var list = mvc.perform(anon("/api/public/directory")).andReturn().getResponse();
    assertThat(list.getHeader("Set-Cookie")).isNull();
    assertThat(list.getHeader("Cache-Control")).contains("public").contains("max-age=60");
    var notFound =
        mvc.perform(anon("/api/public/directory/practitioners/nope-" + tag))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse();
    assertThat(notFound.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(notFound.getHeader("Set-Cookie")).isNull();
  }

  @Test
  void longBioIsCutAtWordBoundary() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID id = practitioner("Long " + tag, "approved", null);
    profile(id, "long-" + tag, "Long " + tag, true, "mot ".repeat(100), "Lyon", null);
    mvc.perform(anon("/api/public/directory?q=Long " + tag))
        .andExpect(jsonPath("$.items[0].bioExcerpt").value(org.hamcrest.Matchers.endsWith("mot…")))
        .andExpect(jsonPath("$.items[0].photoUrl").doesNotExist());
  }
}
