package com.psymate.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.clinical.ClinicalFixtures;
import com.psymate.clinical.ClinicalTestConfig;
import com.psymate.clinical.ClinicalTestConfig.MutableClock;
import com.psymate.clinical.api.SessionFacade;
import com.psymate.clinical.api.SessionView;
import com.psymate.clinical.model.EntryType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/** M15-02 : SessionFacade.closedSession (G Q85, Q86). */
@SpringBootTest
@ActiveProfiles("test")
@Import(ClinicalTestConfig.class)
class SessionFacadeIT {

  private static final Instant T0 = Instant.parse("2026-11-20T09:00:00Z");

  @Autowired private SessionFacade facade;
  @Autowired private SessionService sessions;
  @Autowired private EntryService entries;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;

  private ClinicalFixtures fx;
  private UUID patient;

  @BeforeEach
  void setUp() {
    fx = new ClinicalFixtures(jdbc);
    patient = fx.newPatient(LocalDate.of(1985, 6, 1));
    clock.set(T0);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  private UUID closedSession(int durationMin) {
    UUID session = sessions.start(patient, new StartSessionCommand("video", null));
    clock.advance(Duration.ofMinutes(durationMin));
    sessions.close(session, new CloseSessionCommand(durationMin, "Compte rendu"));
    return session;
  }

  @Test
  void closedSessionReturnsTheViewOfAClosedSession() {
    UUID session = closedSession(50);

    assertThat(facade.closedSession(session))
        .hasValueSatisfying(
            v -> {
              assertThat(v.id()).isEqualTo(session);
              assertThat(v.patientId()).isEqualTo(patient);
              assertThat(v.practitionerId()).isEqualTo(fx.practitioner);
              assertThat(v.kind()).isEqualTo("session");
              assertThat(v.modality()).isEqualTo("video");
              assertThat(v.occurredFrom()).isEqualTo(T0);
              assertThat(v.closedAt()).isEqualTo(T0.plus(Duration.ofMinutes(50)));
              assertThat(v.durationMin()).isEqualTo(50);
            });
  }

  @Test
  void closedSessionReturnsAContactWhichIsBornClosed() {
    UUID contact =
        sessions.contact(
            patient, new ContactCommand("email", T0.minus(Duration.ofDays(1)), "Échange", null));

    assertThat(facade.closedSession(contact))
        .hasValueSatisfying(
            v -> {
              assertThat(v.kind()).isEqualTo("contact");
              assertThat(v.modality()).isEqualTo("email");
              assertThat(v.closedAt()).isEqualTo(T0);
              assertThat(v.durationMin()).isNull();
            });
  }

  @Test
  void closedSessionIsEmptyForAnOpenSession() {
    UUID open = sessions.start(patient, new StartSessionCommand("in_person", null));

    assertThat(facade.closedSession(open)).isEmpty();
  }

  @Test
  void closedSessionIsEmptyForAnArchivedClosedSession() {
    UUID session = closedSession(30);
    entries.archive(session);

    assertThat(facade.closedSession(session)).isEmpty();
  }

  @Test
  void closedSessionIsEmptyForAnUnknownIdOrAnEntryThatIsNoSession() {
    UUID session = sessions.start(patient, new StartSessionCommand("in_person", null));
    UUID note =
        entries.create(
            patient,
            new CreateEntryCommand(
                EntryType.NOTE, session, null, "Une note", null, null, null, null, null, null));

    assertThat(facade.closedSession(UUID.randomUUID())).isEmpty();
    assertThat(facade.closedSession(note)).isEmpty();
  }

  @Test
  void closedSessionIsEmptyForAnotherPractitionerOrWithoutPrincipal() {
    UUID session = closedSession(45);

    fx.authenticate(fx.newPractitioner());
    assertThat(facade.closedSession(session)).isEmpty();

    SecurityContextHolder.clearContext();
    assertThat(facade.closedSession(session)).isEmpty();

    fx.authenticate(fx.practitioner);
    assertThat(facade.closedSession(session)).map(SessionView::id).contains(session);
  }
}
