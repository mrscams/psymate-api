package com.psymate.directory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.directory.service.PhotoStorage;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, ProfileIT.Recording.class})
class ProfileIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PhotoStorage storage;
  @Autowired private Recorder recorder;

  static class Recorder {
    final java.util.List<UUID> published = new java.util.concurrent.CopyOnWriteArrayList<>();
    final java.util.List<UUID> unpublished = new java.util.concurrent.CopyOnWriteArrayList<>();

    @org.springframework.context.event.EventListener
    void on(com.psymate.directory.api.ProfilePublished e) {
      published.add(e.getPractitionerId());
    }

    @org.springframework.context.event.EventListener
    void on(com.psymate.directory.api.ProfileUnpublished e) {
      unpublished.add(e.getPractitionerId());
    }
  }

  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  static class Recording {
    @org.springframework.context.annotation.Bean
    Recorder recorder() {
      return new Recorder();
    }
  }

  private RequestPostProcessor auth;
  private UUID tenant;
  private UUID alice;

  @BeforeEach
  void practitioners() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    alice = insertPractitioner("Dr Alice Martin");
    as(alice, "Dr Alice Martin");
  }

  private void as(UUID practitioner, String displayName) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitioner,
            tenant,
            practitioner + "@example.test",
            displayName,
            "psychologist",
            "none");
    auth =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private UUID insertPractitioner(String displayName) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', ?, 'psychologist', 'approved')",
        id,
        tenant,
        email,
        email,
        displayName);
    return id;
  }

  private ResultActions putProfile(String json) throws Exception {
    return mvc.perform(
        put("/api/me/profile").contentType(MediaType.APPLICATION_JSON).content(json).with(auth));
  }

  private ResultActions call(org.springframework.test.web.servlet.RequestBuilder b)
      throws Exception {
    return mvc.perform(b);
  }

  private static String completeProfile(String extra) {
    return "{\"displayName\":\"Dr Alice Martin\",\"city\":\"Lyon\",\"contactEmail\":\"a@b.fr\","
        + "\"showEmail\":true"
        + extra
        + "}";
  }

  // ---- lecture, création, slug

  @Test
  void getCreatesEmptyProfileWithDisplayNameAndSlug() throws Exception {
    mvc.perform(get("/api/me/profile").with(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Dr Alice Martin"))
        .andExpect(jsonPath("$.slug").value(org.hamcrest.Matchers.startsWith("alice-martin")))
        .andExpect(jsonPath("$.published").value(false))
        .andExpect(jsonPath("$.availability").value("unknown"))
        .andExpect(jsonPath("$.specialties").isEmpty());
    mvc.perform(get("/api/me/profile").with(auth)).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from practitioner_profile where practitioner_id = ?",
                Integer.class,
                alice))
        .isEqualTo(1);
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(get("/api/me/profile")).andExpect(status().is4xxClientError());
  }

  @Test
  void twoClaireLambertGetDistinctSlugs() throws Exception {
    UUID c1 = insertPractitioner("Claire Lambert");
    UUID c2 = insertPractitioner("Claire Lambert");
    as(c1, "Claire Lambert");
    mvc.perform(get("/api/me/profile").with(auth))
        .andExpect(jsonPath("$.slug").value("claire-lambert"));
    as(c2, "Claire Lambert");
    mvc.perform(get("/api/me/profile").with(auth))
        .andExpect(jsonPath("$.slug").value("claire-lambert-2"));
  }

  // ---- édition

  @Test
  void putUpdatesEditableColumnsAndSearchVector() throws Exception {
    putProfile(
            "{\"displayName\":\"Alice M.\",\"bio\":\"Accompagnement des anxieux\","
                + "\"specialties\":[\"anxiety\",\"emdr\"],\"audiences\":[\"adults\"],"
                + "\"modalities\":[\"video\",\"in_person\"],\"languages\":[\"fr\",\"en\"],"
                + "\"city\":\"Lyon\",\"websiteUrl\":\"https://alice.example\","
                + "\"availability\":\"waitlist\",\"showPhone\":true,\"contactPhone\":\"0102030405\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Alice M."))
        .andExpect(jsonPath("$.specialties[0]").value("anxiety"))
        .andExpect(jsonPath("$.availability").value("waitlist"))
        .andExpect(jsonPath("$.country").value("FR"));
    assertThat(
            jdbc.queryForObject(
                "select search_vector @@ plainto_tsquery('simple', 'lyon') from practitioner_profile"
                    + " where practitioner_id = ?",
                Boolean.class,
                alice))
        .isTrue();
  }

  @Test
  void specialtyOutsideListIsRejectedWith422() throws Exception {
    putProfile("{\"displayName\":\"Alice\",\"specialties\":[\"voyance\"]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("specialties"));
  }

  @Test
  void deactivatedSpecialtyIsRejected() throws Exception {
    jdbc.update("update directory_vocabulary set active = false where code = 'burnout'");
    try {
      putProfile("{\"displayName\":\"Alice\",\"specialties\":[\"burnout\"]}")
          .andExpect(status().isUnprocessableEntity());
    } finally {
      jdbc.update("update directory_vocabulary set active = true where code = 'burnout'");
    }
  }

  @Test
  void invalidLanguageModalityAvailabilityAndUrlAreRejected() throws Exception {
    putProfile(
            "{\"displayName\":\"Alice\",\"languages\":[\"fra\"],\"modalities\":[\"phone\"],"
                + "\"availability\":\"maybe\",\"websiteUrl\":\"ftp://x.fr\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(4));
  }

  @Test
  void authorTypeAndPublishedCannotBeWrittenThroughPut() throws Exception {
    putProfile(completeProfile(",\"published\":true,\"authorType\":\"patient\",\"slug\":\"x\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.published").value(false))
        .andExpect(jsonPath("$.slug").value(org.hamcrest.Matchers.startsWith("alice-martin")));
  }

  // ---- vocabulaire

  @Test
  void directoryVocabularyServesClosedLists() throws Exception {
    mvc.perform(get("/api/vocabulary/directory").with(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.specialties.length()").value(org.hamcrest.Matchers.greaterThan(19)))
        .andExpect(jsonPath("$.specialties[?(@.code=='emdr')].labelFr").value("EMDR"))
        // Les 5 publics du seed V04 ; d'autres tests en ajoutent par l'API admin dans la base
        // partagée, le compte exact du seed est vérifié par FlywayMigrationIT (base dédiée).
        .andExpect(
            jsonPath("$.audiences[*].code")
                .value(
                    org.hamcrest.Matchers.hasItems(
                        "adults", "young_adults", "adolescents", "children", "couples_families")))
        .andExpect(jsonPath("$.modalities.length()").value(3))
        .andExpect(jsonPath("$.languages[?(@.code=='fr')].labelFr").value("français"));
  }

  // ---- publication

  @Test
  void publishWithoutContactIsRefusedWithMissingContact() throws Exception {
    putProfile("{\"displayName\":\"Alice\",\"city\":\"Lyon\",\"contactEmail\":\"a@b.fr\"}")
        .andExpect(status().isOk());
    mvc.perform(post("/api/me/profile/publish").with(auth))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.missing[0]").value("contact"))
        .andExpect(jsonPath("$.missing.length()").value(1));
  }

  @Test
  void publishWithoutCityIsRefusedWithMissingCity() throws Exception {
    putProfile("{\"displayName\":\"Alice\",\"contactEmail\":\"a@b.fr\",\"showEmail\":true}")
        .andExpect(status().isOk());
    mvc.perform(post("/api/me/profile/publish").with(auth))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.missing[0]").value("city"))
        .andExpect(jsonPath("$.missing.length()").value(1));
  }

  @Test
  void publishThenUnpublishKeepsPublishedAt() throws Exception {
    putProfile(completeProfile("")).andExpect(status().isOk());
    mvc.perform(post("/api/me/profile/publish").with(auth)).andExpect(status().isNoContent());
    mvc.perform(get("/api/me/profile").with(auth))
        .andExpect(jsonPath("$.published").value(true))
        .andExpect(jsonPath("$.publishedAt").isNotEmpty());
    var publishedAt =
        jdbc.queryForObject(
            "select published_at from practitioner_profile where practitioner_id = ?",
            java.sql.Timestamp.class,
            alice);
    assertThat(publishedAt).isNotNull();

    mvc.perform(post("/api/me/profile/unpublish").with(auth)).andExpect(status().isNoContent());
    mvc.perform(get("/api/me/profile").with(auth)).andExpect(jsonPath("$.published").value(false));
    assertThat(
            jdbc.queryForObject(
                "select published_at from practitioner_profile where practitioner_id = ?",
                java.sql.Timestamp.class,
                alice))
        .isEqualTo(publishedAt);
    // idempotent
    mvc.perform(post("/api/me/profile/unpublish").with(auth)).andExpect(status().isNoContent());
  }

  @Test
  void publishAndUnpublishRecordDomainEvents() throws Exception {
    putProfile(completeProfile("")).andExpect(status().isOk());
    mvc.perform(post("/api/me/profile/publish").with(auth)).andExpect(status().isNoContent());
    mvc.perform(post("/api/me/profile/unpublish").with(auth)).andExpect(status().isNoContent());
    assertThat(recorder.published).containsExactly(alice);
    assertThat(recorder.unpublished).containsExactly(alice);
  }

  // ---- photo

  /**
   * JPEG 1000x600 dans lequel on injecte un segment APP1 EXIF (avec un marqueur reconnaissable).
   */
  static byte[] jpegWithExif() throws Exception {
    BufferedImage img = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < 1000; x++) {
      for (int y = 0; y < 600; y++) {
        img.setRGB(x, y, (x * 255 / 1000) << 16 | (y * 255 / 600) << 8);
      }
    }
    ByteArrayOutputStream plain = new ByteArrayOutputStream();
    ImageIO.write(img, "jpg", plain);
    byte[] jpeg = plain.toByteArray();

    byte[] payload =
        ("Exif\0\0MM\0*\0\0\0\10\0\0\0\0\0\0" + "GPS-SECRET-48.85N")
            .getBytes(StandardCharsets.ISO_8859_1);
    int segmentLength = payload.length + 2;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2); // SOI
    out.write(0xFF);
    out.write(0xE1);
    out.write(segmentLength >> 8);
    out.write(segmentLength & 0xFF);
    out.write(payload);
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  private static boolean contains(byte[] haystack, String needle) {
    return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
  }

  private ResultActions uploadPhoto(byte[] bytes, String contentType) throws Exception {
    return mvc.perform(
        multipart("/api/me/profile/photo")
            .file(new MockMultipartFile("file", "photo", contentType, bytes))
            .with(auth));
  }

  @Test
  void uploadedPhotoIsSquare512WithoutExif() throws Exception {
    byte[] input = jpegWithExif();
    assertThat(contains(input, "GPS-SECRET")).isTrue();

    String key =
        com.jayway.jsonpath.JsonPath.read(
            uploadPhoto(input, "image/jpeg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoAssetKey").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.photoAssetKey");

    byte[] stored = storage.read(key);
    assertThat(contains(stored, "Exif")).isFalse();
    assertThat(contains(stored, "GPS-SECRET")).isFalse();
    BufferedImage read = ImageIO.read(new ByteArrayInputStream(stored));
    assertThat(read.getWidth()).isEqualTo(512);
    assertThat(read.getHeight()).isEqualTo(512);

    // remplacer la photo supprime l'ancien objet ; supprimer la photo vide la clé
    uploadPhoto(input, "image/jpeg").andExpect(status().isOk());
    assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> storage.read(key))).isNotNull();
    mvc.perform(delete("/api/me/profile/photo").with(auth)).andExpect(status().isNoContent());
    mvc.perform(get("/api/me/profile").with(auth))
        .andExpect(jsonPath("$.photoAssetKey").doesNotExist());
  }

  @Test
  void pngIsAccepted() throws Exception {
    BufferedImage img = new BufferedImage(200, 300, BufferedImage.TYPE_INT_ARGB);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(img, "png", png);
    uploadPhoto(png.toByteArray(), "image/png").andExpect(status().isOk());
  }

  @Test
  void photoWithWrongSignatureIsRejected() throws Exception {
    uploadPhoto("<html>not an image</html>".getBytes(StandardCharsets.UTF_8), "image/jpeg")
        .andExpect(status().isUnsupportedMediaType());
    uploadPhoto(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3}, "image/jpeg")
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void photoOver5MbIsRejected() throws Exception {
    byte[] big = new byte[5 * 1024 * 1024 + 1];
    big[0] = (byte) 0xFF;
    big[1] = (byte) 0xD8;
    big[2] = (byte) 0xFF;
    uploadPhoto(big, "image/jpeg")
        .andExpect(status().is(413))
        .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));
  }

  @Test
  void imageWithTooManyPixelsIsRejected() throws Exception {
    // 6000 x 6000 = 36 M pixels, 1 bit par pixel : quelques Ko une fois compressé en PNG
    BufferedImage img = new BufferedImage(6000, 6000, BufferedImage.TYPE_BYTE_BINARY);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(img, "png", png);
    assertThat(png.size()).isLessThan(5 * 1024 * 1024);
    uploadPhoto(png.toByteArray(), "image/png")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE_DIMENSIONS"));
  }

  @Test
  void freeTextFieldsAreBounded() throws Exception {
    String longText = "x".repeat(600);
    putProfile(
            "{\"displayName\":\"Alice\",\"fees\":\""
                + longText
                + "\",\"address\":\""
                + longText
                + "\",\"contactPhone\":\""
                + longText
                + "\",\"titleLabel\":\""
                + longText
                + "\",\"responseDelay\":\""
                + longText
                + "\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(5));
  }

  @Test
  void anotherTenantCannotSeeOrModifyMyProfile() throws Exception {
    putProfile(completeProfile(",\"bio\":\"Profil de A\"")).andExpect(status().isOk());

    UUID otherTenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Autre cabinet')", otherTenant);
    UUID bob = UUID.randomUUID();
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Bob', 'B', 'Bob Durand', 'coach')",
        bob,
        otherTenant,
        bob + "@example.test",
        bob + "@example.test");
    CurrentPractitioner principal =
        new CurrentPractitioner(
            bob, otherTenant, bob + "@example.test", "Bob Durand", "coach", "none");
    RequestPostProcessor bobAuth =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

    mvc.perform(get("/api/me/profile").with(bobAuth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Bob Durand"))
        .andExpect(jsonPath("$.bio").doesNotExist());
    mvc.perform(
            put("/api/me/profile")
                .with(bobAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Bob modifie\",\"bio\":\"Profil de B\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/me/profile/publish").with(bobAuth)).andExpect(status().isConflict());

    assertThat(
            jdbc.queryForObject(
                "select bio from practitioner_profile where practitioner_id = ?",
                String.class,
                alice))
        .isEqualTo("Profil de A");
    assertThat(
            jdbc.queryForObject(
                "select display_name from practitioner_profile where practitioner_id = ?",
                String.class,
                alice))
        .isEqualTo("Dr Alice Martin");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from practitioner_profile where practitioner_id in (?, ?)",
                Integer.class,
                alice,
                bob))
        .isEqualTo(2);
  }
}
