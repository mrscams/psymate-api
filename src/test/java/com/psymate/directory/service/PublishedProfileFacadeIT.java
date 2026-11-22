package com.psymate.directory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.directory.api.PublishedProfileFacade;
import com.psymate.directory.api.PublishedProfileView;
import com.psymate.testsupport.SharedPostgres;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * M14-03 : {@link PublishedProfileFacade} ne rend une vue que pour une fiche publiée d'un praticien
 * approuvé ; tout autre cas donne une réponse vide, sans distinction (G Q71, Q78, Q48 (2)).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class PublishedProfileFacadeIT {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PublishedProfileFacade facade;

  private UUID tenant;

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet façade')", tenant);
  }

  private UUID practitioner(String status) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'T', 'P', 'Dr Compte', 'psychologist', ?)",
        id,
        tenant,
        email,
        email,
        status);
    return id;
  }

  /** Fiche du praticien ; le nom affiché diffère exprès de celui du compte. */
  private String profile(UUID practitioner, boolean published) {
    String slug = "facade-" + practitioner;
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, display_name, published,"
            + " contact_email, contact_phone, city) values (?, ?, ?, ?, ?, ?, ?)",
        practitioner,
        slug,
        "Dr Fiche Publique",
        published,
        "cabinet@example.test",
        "0102030405",
        "Lyon");
    return slug;
  }

  @Test
  void publishedProfileOfApprovedPractitionerGivesViewBySlugAndById() {
    UUID id = practitioner("approved");
    String slug = profile(id, true);
    PublishedProfileView expected = new PublishedProfileView(id, tenant, slug, "Dr Fiche Publique");

    assertThat(facade.bySlug(slug)).contains(expected);
    assertThat(facade.byPractitioner(id)).contains(expected);
  }

  @Test
  void viewHasExactlyTheFourPublicFields() {
    assertThat(PublishedProfileView.class.getRecordComponents())
        .extracting(c -> c.getName())
        .containsExactly("practitionerId", "tenantId", "slug", "displayName");
  }

  @Test
  void unknownSlugOrPractitionerGivesEmpty() {
    assertThat(facade.bySlug("inconnu-" + UUID.randomUUID())).isEmpty();
    assertThat(facade.byPractitioner(UUID.randomUUID())).isEmpty();
  }

  @Test
  void approvedPractitionerWithoutProfileGivesEmpty() {
    UUID id = practitioner("approved");

    assertThat(facade.byPractitioner(id)).isEmpty();
  }

  @Test
  void unpublishedProfileGivesEmptyBySlugAndById() {
    UUID id = practitioner("approved");
    String slug = profile(id, false);

    assertThat(facade.bySlug(slug)).isEmpty();
    assertThat(facade.byPractitioner(id)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"suspended", "rejected", "closed", "under_review"})
  void publishedProfileOfNonApprovedPractitionerGivesEmptyBySlugAndById(String status) {
    UUID id = practitioner(status);
    String slug = profile(id, true);

    assertThat(facade.bySlug(slug)).isEmpty();
    assertThat(facade.byPractitioner(id)).isEmpty();
  }

  @Test
  void nullOrBlankArgumentsGiveEmpty() {
    assertThat(facade.bySlug(null)).isEmpty();
    assertThat(facade.bySlug("")).isEmpty();
    assertThat(facade.bySlug("   ")).isEmpty();
    assertThat(facade.byPractitioner(null)).isEmpty();
  }

  @Test
  void slugMatchIsExact() {
    UUID id = practitioner("approved");
    String slug = profile(id, true);

    assertThat(facade.bySlug(slug.toUpperCase())).isEmpty();
    assertThat(facade.bySlug(" " + slug)).isEmpty();
  }
}
