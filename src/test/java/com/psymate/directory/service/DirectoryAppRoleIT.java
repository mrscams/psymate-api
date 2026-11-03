package com.psymate.directory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.directory.DirectoryTestFixtures;
import com.psymate.directory.dto.CreateRequest;
import com.psymate.directory.dto.ItemDto;
import com.psymate.directory.dto.PatchRequest;
import com.psymate.directory.dto.ProfileDto;
import com.psymate.directory.model.ContactRequest;
import com.psymate.directory.repository.ContactRequestRepository;
import com.psymate.testsupport.AppRolePostgres;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Le module directory tourne ici avec le rôle psymate_app (Flyway avec le propriétaire) : les
 * écritures JPA du profil, du vocabulaire et des demandes de contact passent là où le rôle a le
 * droit, y compris le DELETE physique de la purge (table technique, E2).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AppRolePostgres.class)
@DirtiesContext
class DirectoryAppRoleIT {

  @Autowired private ProfileService profiles;
  @Autowired private DirectoryVocabularyAdminService vocabulary;
  @Autowired private ContactRequestService contacts;
  @Autowired private ContactRequestRepository contactRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate tx;

  private DirectoryTestFixtures fx;
  private UUID alice;

  @BeforeEach
  void practitioner() {
    assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("psymate_app");
    fx = new DirectoryTestFixtures(jdbc);
    alice = fx.newPractitioner();
    fx.loginAs(alice);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void profileIsCreatedUpdatedPublishedAndUnpublishedUnderTheApplicationRole() {
    ProfileDto created = profiles.get();
    assertThat(created.published()).isFalse();

    ProfileDto edited = profiles.update(DirectoryTestFixtures.completeProfile("Bio"));
    assertThat(edited.bio()).isEqualTo("Bio");

    profiles.publish();
    assertThat(profiles.get().published()).isTrue();
    assertThat(profiles.get().publishedAt()).isNotNull();

    profiles.unpublish();
    assertThat(profiles.get().published()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from practitioner_profile where practitioner_id = ?",
                Integer.class,
                alice))
        .isEqualTo(1);
  }

  @Test
  void vocabularyEntryIsCreatedThenModifiedUnderTheApplicationRole() {
    String code = "role_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    ItemDto created = vocabulary.create(new CreateRequest("audience", code, "Public", null, null));
    assertThat(created.active()).isTrue();
    assertThat(created.usedByPublishedProfiles()).isZero();

    ItemDto patched =
        vocabulary.update("audience", code, new PatchRequest("Public modifié", "Edited", 7, false));
    assertThat(patched.labelFr()).isEqualTo("Public modifié");
    assertThat(patched.labelEn()).isEqualTo("Edited");
    assertThat(patched.sortOrder()).isEqualTo(7);
    assertThat(patched.active()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select active from directory_vocabulary where kind = 'audience' and code = ?",
                Boolean.class,
                code))
        .isFalse();
  }

  @Test
  void contactRequestIsInsertedRelayedAndPurgedUnderTheApplicationRole() {
    OffsetDateTime now = OffsetDateTime.now();
    UUID id = UUID.randomUUID();
    tx.executeWithoutResult(
        s ->
            contactRepository.save(
                new ContactRequest(
                    id,
                    alice,
                    "Camille",
                    "camille@example.test",
                    null,
                    "Bonjour",
                    now,
                    "hash",
                    now,
                    now.minusDays(1))));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from contact_request where id = ?", Integer.class, id))
        .isEqualTo(1);

    tx.executeWithoutResult(
        s -> {
          ContactRequest stored = contactRepository.findById(id).orElseThrow();
          stored.markRelayFailed("smtp down");
        });
    assertThat(
            jdbc.queryForObject(
                "select relay_error from contact_request where id = ?", String.class, id))
        .isEqualTo("smtp down");

    tx.executeWithoutResult(
        s -> contactRepository.findById(id).orElseThrow().markRelayed(OffsetDateTime.now()));
    var row = jdbc.queryForMap("select * from contact_request where id = ?", id);
    assertThat(row.get("relayed_at")).isNotNull();
    assertThat(row.get("relay_error")).as("un relais abouti efface l'erreur").isNull();

    assertThat(contacts.purgeDue()).isGreaterThanOrEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from contact_request where id = ?", Integer.class, id))
        .as("DELETE physique sous le rôle applicatif")
        .isZero();
  }
}
