package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.testsupport.SharedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * V16 (M16-02, G Q97) : {@code practitioner.locale} et {@code booking_request.locale} acceptent
 * {@code ar} en plus de {@code fr} et {@code en} ; toute autre valeur reste refusée par la base.
 * Les deux contraintes gardent le nom que PostgreSQL leur avait donné (V01, V15).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class LocaleArabicMigrationIT {

  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  private String definition(String table, String constraint) {
    return jdbc.queryForObject(
        "select pg_get_constraintdef(c.oid) from pg_constraint c join pg_class t on t.oid ="
            + " c.conrelid where t.relname = ? and c.conname = ?",
        String.class,
        table,
        constraint);
  }

  private UUID practitioner(String locale) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, locale)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist', ?)",
        id,
        tenant,
        email,
        email,
        locale);
    return id;
  }

  private void bookingRequest(UUID practitioner, String locale) {
    Instant startsAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    jdbc.update(
        "insert into booking_request (id, tenant_id, practitioner_id, starts_at, ends_at,"
            + " modality, first_name, last_name, email, locale, email_hash, verify_token_hash,"
            + " verify_expires_at) values (?, ?, ?, ?, ?, 'video', 'Sara', 'Benali',"
            + " 'sara@example.test', ?, ?, ?, ?)",
        UUID.randomUUID(),
        tenant,
        practitioner,
        Timestamp.from(startsAt),
        Timestamp.from(startsAt.plus(60, ChronoUnit.MINUTES)),
        locale,
        "test-" + UUID.randomUUID(),
        "test-" + UUID.randomUUID(),
        Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
  }

  @Test
  void v16IsApplied() {
    assertThat(
            jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank",
                String.class))
        .contains("16");
    assertThat(definition("practitioner", "practitioner_locale_check"))
        .contains("'fr'", "'en'", "'ar'");
    assertThat(definition("booking_request", "booking_request_locale_check"))
        .contains("'fr'", "'en'", "'ar'");
  }

  @Test
  void arIsAcceptedAndOtherValuesAreStillRefused() {
    UUID arabic = practitioner("ar");
    assertThat(
            jdbc.queryForObject(
                "select locale from practitioner where id = ?", String.class, arabic))
        .isEqualTo("ar");
    practitioner("fr");
    practitioner("en");
    bookingRequest(arabic, "ar");
    bookingRequest(arabic, "en");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_request where practitioner_id = ? and locale = 'ar'",
                Integer.class,
                arabic))
        .isEqualTo(1);

    for (String refused : new String[] {"de", "AR", "ar-MA", ""}) {
      assertThatThrownBy(() -> practitioner(refused))
          .as(refused)
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("practitioner_locale_check");
      assertThatThrownBy(() -> bookingRequest(arabic, refused))
          .as(refused)
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("booking_request_locale_check");
    }
  }
}
