package com.psymate.agenda.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentException;
import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.AppointmentStatus;
import com.psymate.testsupport.SharedPostgres;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persistance JPA de {@code appointment} (M11-02, V12) sur une vraie base (Testcontainers
 * pgvector/pgvector:pg16, {@code ddl-auto=validate}) : aller-retour de l'entité et de ses
 * énumérations en texte, lecture bornée au praticien (G Q58), modification avec {@code updated_at}
 * maintenu par l'entité, annulation qui pose {@code cancelled} et {@code cancelled_at} ensemble, et
 * les contraintes {@code ck_appointment_order}, {@code ck_appointment_cancel} et les CHECK des
 * énumérations qui refusent une ligne invalide.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class AgendaPersistenceIT {

  @Autowired private AppointmentRepository appointments;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate tx;

  @Value("${spring.jpa.hibernate.ddl-auto}")
  private String ddlAuto;

  private UUID tenant;
  private UUID alice;
  private UUID bob;
  private UUID patient;
  private OffsetDateTime now;

  @BeforeEach
  void fixtures() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet agenda')", tenant);
    alice = practitioner();
    bob = practitioner();
    patient = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'Théo B.')",
        patient,
        tenant,
        alice);
    now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  private UUID practitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'T', 'P', 'Dr Agenda', 'psychologist', 'approved')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  private Appointment planned(OffsetDateTime startsAt, int durationMin, boolean tentative) {
    return new Appointment(
        UUID.randomUUID(),
        tenant,
        alice,
        patient,
        null,
        startsAt,
        startsAt.plusMinutes(durationMin),
        AppointmentModality.in_person,
        tentative,
        "Reprendre le point du 3 mars",
        now);
  }

  private Map<String, Object> row(UUID id) {
    return jdbc.queryForMap("select * from appointment where id = ?", id);
  }

  @Test
  void theEntityIsValidatedAgainstTheRealSchema() {
    assertThat(ddlAuto).isEqualTo("validate");
  }

  @Test
  void insertThenReadBackWithEnumsStoredAsText() {
    Appointment saved = appointments.save(planned(now.plusDays(1), 45, true));

    Map<String, Object> row = row(saved.getId());
    assertThat(row.get("status")).isEqualTo("tentative");
    assertThat(row.get("modality")).isEqualTo("in_person");
    assertThat(row.get("series_id")).isNull();
    assertThat(row.get("cancelled_at")).isNull();
    assertThat(row.get("session_entry_id")).isNull();
    assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(now.toInstant());
    assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(now.toInstant());

    Appointment read = appointments.findByIdAndPractitionerId(saved.getId(), alice).orElseThrow();
    assertThat(read.isNew()).isFalse();
    assertThat(read.getStatus()).isEqualTo(AppointmentStatus.tentative);
    assertThat(read.getModality()).isEqualTo(AppointmentModality.in_person);
    assertThat(read.getDurationMin()).isEqualTo(45);
    assertThat(read.getStartsAt().toInstant()).isEqualTo(now.plusDays(1).toInstant());
    assertThat(read.getPlanningNote()).isEqualTo("Reprendre le point du 3 mars");
    assertThat(read.getPatientId()).isEqualTo(patient);
    assertThat(read.getTenantId()).isEqualTo(tenant);
  }

  /** G Q58 : le rendez-vous d'un autre praticien est introuvable. */
  @Test
  void readIsBoundToTheOwningPractitioner() {
    Appointment saved = appointments.save(planned(now.plusDays(2), 60, false));

    assertThat(appointments.findByIdAndPractitionerId(saved.getId(), bob)).isEmpty();
    assertThat(appointments.findByIdAndPractitionerId(saved.getId(), alice)).isPresent();
  }

  @Test
  void rescheduleAndNoteChangesArePersistedAndTouchUpdatedAt() {
    Appointment saved = appointments.save(planned(now.plusDays(3), 60, false));
    OffsetDateTime later = now.plusHours(2);
    OffsetDateTime newStart = now.plusDays(4);

    tx.executeWithoutResult(
        s -> {
          Appointment a = appointments.findById(saved.getId()).orElseThrow();
          a.reschedule(newStart, newStart.plusMinutes(30), later);
          a.changeModality(AppointmentModality.video, later);
          a.changePlanningNote(null, later);
        });

    Map<String, Object> row = row(saved.getId());
    assertThat(((Timestamp) row.get("starts_at")).toInstant()).isEqualTo(newStart.toInstant());
    assertThat(((Timestamp) row.get("ends_at")).toInstant())
        .isEqualTo(newStart.plusMinutes(30).toInstant());
    assertThat(row.get("modality")).isEqualTo("video");
    assertThat(row.get("planning_note")).isNull();
    assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(now.toInstant());
    assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(later.toInstant());
  }

  /** G Q53 : annulation = statut et date ensemble, la ligne reste ; idempotente. */
  @Test
  void cancelKeepsTheRowAndSatisfiesTheCancelCheck() {
    Appointment saved = appointments.save(planned(now.plusDays(5), 60, false));
    OffsetDateTime at = now.plusMinutes(5);

    tx.executeWithoutResult(
        s -> appointments.findById(saved.getId()).orElseThrow().cancel("Patient absent", at));
    tx.executeWithoutResult(
        s ->
            appointments
                .findById(saved.getId())
                .orElseThrow()
                .cancel("Autre motif", at.plusHours(1)));

    Map<String, Object> row = row(saved.getId());
    assertThat(row.get("status")).isEqualTo("cancelled");
    assertThat(row.get("cancel_reason")).isEqualTo("Patient absent");
    assertThat(((Timestamp) row.get("cancelled_at")).toInstant()).isEqualTo(at.toInstant());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment where id = ?", Integer.class, saved.getId()))
        .isEqualTo(1);

    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s ->
                        appointments
                            .findById(saved.getId())
                            .orElseThrow()
                            .reschedule(now.plusDays(6), now.plusDays(6).plusHours(1), at)))
        .isInstanceOfSatisfying(
            AppointmentException.class,
            e -> {
              assertThat(e.code()).isEqualTo("APPOINTMENT_NOT_ACTIVE");
              assertThat(e.status().value()).isEqualTo(409);
            });
  }

  @Test
  void confirmMovesTentativeToPlannedAndRefusesTheRest() {
    Appointment saved = appointments.save(planned(now.plusDays(7), 60, true));

    tx.executeWithoutResult(s -> appointments.findById(saved.getId()).orElseThrow().confirm(now));
    assertThat(row(saved.getId()).get("status")).isEqualTo("planned");

    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> appointments.findById(saved.getId()).orElseThrow().confirm(now)))
        .isInstanceOfSatisfying(
            AppointmentException.class,
            e -> assertThat(e.code()).isEqualTo("APPOINTMENT_NOT_ACTIVE"));
  }

  // ---- contraintes du DDL

  @Test
  void ckAppointmentOrderRejectsAnEmptyOrReversedSlot() {
    OffsetDateTime start = now.plusDays(8);
    Appointment reversed =
        new Appointment(
            UUID.randomUUID(),
            tenant,
            alice,
            patient,
            null,
            start,
            start.minusMinutes(30),
            AppointmentModality.phone,
            false,
            null,
            now);

    assertThatThrownBy(() -> appointments.save(reversed))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_appointment_order");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into appointment (id, tenant_id, practitioner_id, patient_id, starts_at,"
                        + " ends_at, modality) values (?, ?, ?, ?, ?, ?, 'video')",
                    UUID.randomUUID(),
                    tenant,
                    alice,
                    patient,
                    Timestamp.from(start.toInstant()),
                    Timestamp.from(start.toInstant())))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_appointment_order");
  }

  @Test
  void ckAppointmentCancelTiesTheStatusToTheDate() {
    OffsetDateTime start = now.plusDays(9);
    String insert =
        "insert into appointment (id, tenant_id, practitioner_id, patient_id, starts_at, ends_at,"
            + " modality, status, cancelled_at) values (?, ?, ?, ?, ?, ?, 'video', ?, ?)";
    Timestamp from = Timestamp.from(start.toInstant());
    Timestamp to = Timestamp.from(start.plusHours(1).toInstant());

    assertThatThrownBy(
            () ->
                jdbc.update(
                    insert, UUID.randomUUID(), tenant, alice, patient, from, to, "cancelled", null))
        .as("cancelled sans cancelled_at")
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_appointment_cancel");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    insert,
                    UUID.randomUUID(),
                    tenant,
                    alice,
                    patient,
                    from,
                    to,
                    "planned",
                    Timestamp.from(now.toInstant())))
        .as("cancelled_at sans le statut cancelled")
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_appointment_cancel");
    assertThat(
            jdbc.update(
                insert,
                UUID.randomUUID(),
                tenant,
                alice,
                patient,
                from,
                to,
                "cancelled",
                Timestamp.from(now.toInstant())))
        .as("les deux ensemble passent")
        .isEqualTo(1);
  }

  @Test
  void enumChecksRejectUnknownValues() {
    OffsetDateTime start = now.plusDays(10);
    String insert =
        "insert into appointment (id, tenant_id, practitioner_id, patient_id, starts_at, ends_at,"
            + " modality, status) values (?, ?, ?, ?, ?, ?, ?, ?)";
    Timestamp from = Timestamp.from(start.toInstant());
    Timestamp to = Timestamp.from(start.plusHours(1).toInstant());

    assertThatThrownBy(
            () ->
                jdbc.update(
                    insert,
                    UUID.randomUUID(),
                    tenant,
                    alice,
                    patient,
                    from,
                    to,
                    "letter",
                    "planned"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("modality");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    insert,
                    UUID.randomUUID(),
                    tenant,
                    alice,
                    patient,
                    from,
                    to,
                    "video",
                    "confirmed"))
        .as("confirmed a disparu (G Q52)")
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("status");
  }
}
