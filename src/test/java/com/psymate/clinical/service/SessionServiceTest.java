package com.psymate.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psymate.clinical.api.SessionClosed;
import com.psymate.clinical.model.ClinicalError;
import com.psymate.clinical.model.ClinicalException;
import com.psymate.clinical.model.Entry;
import com.psymate.clinical.model.EntryStatus;
import com.psymate.clinical.model.EntryType;
import com.psymate.clinical.model.EntryVersion;
import com.psymate.clinical.model.SessionDetail;
import com.psymate.clinical.model.TimeSpan;
import com.psymate.clinical.model.Visibility;
import com.psymate.clinical.repository.EntryRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.patients.api.PatientOwnershipGuard;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * M15-02 : règle de publication de {@link SessionClosed} par {@link SessionService}, sans base
 * (collaborateurs simulés ; la base réelle est couverte par SessionServiceIT et SessionClosedIT).
 */
class SessionServiceTest {

  private static final Instant FROM = Instant.parse("2026-11-20T09:00:00Z");
  private static final Instant NOW = FROM.plus(Duration.ofMinutes(50));

  private final UUID tenant = UUID.randomUUID();
  private final UUID practitioner = UUID.randomUUID();
  private final UUID patient = UUID.randomUUID();
  private final UUID session = UUID.randomUUID();

  private final EntryRepository repo = mock(EntryRepository.class);
  private final EntryService entries = mock(EntryService.class);
  private final PractitionerFacade practitioners = mock(PractitionerFacade.class);
  private final List<Object> published = new ArrayList<>();

  private SessionService service;

  @BeforeEach
  void setUp() {
    PatientOwnershipGuard guard = id -> {};
    service =
        new SessionService(
            repo,
            entries,
            mock(EntryValidators.class),
            guard,
            practitioners,
            published::add,
            Clock.fixed(NOW, ZoneOffset.UTC));
    CurrentPractitioner me =
        new CurrentPractitioner(
            practitioner, tenant, "dr@example.test", "Dr Test", "psychologist", "none");
    SecurityContextHolder.getContext()
        .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(me, null, List.of()));
    when(repo.findForUpdate(session)).thenReturn(Optional.of(sessionEntry(null)));
    when(repo.currentVersion(any()))
        .thenReturn(
            Optional.of(
                new EntryVersion(
                    UUID.randomUUID(), session, 2, null, null, null, null, practitioner, NOW)));
    when(practitioners.preferencesOf(practitioner)).thenReturn(Map.of());
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  private Entry sessionEntry(Instant archivedAt) {
    return new Entry(
        session,
        tenant,
        patient,
        EntryType.SESSION,
        EntryStatus.FINAL,
        Visibility.CLINICAL,
        new TimeSpan(FROM, null, TimeSpan.Precision.EXACT, null),
        null,
        UUID.randomUUID(),
        1,
        0,
        "practitioner",
        archivedAt,
        practitioner,
        FROM,
        FROM);
  }

  private List<SessionClosed> closedEvents() {
    return published.stream()
        .filter(SessionClosed.class::isInstance)
        .map(SessionClosed.class::cast)
        .toList();
  }

  @Test
  void closePublishesExactlyOneSessionClosedWithTheRetainedDurationAndInstant() {
    when(repo.session(session))
        .thenReturn(Optional.of(new SessionDetail("session", "video", null, null)));

    service.close(session, new CloseSessionCommand(45, "Compte rendu"));

    assertThat(closedEvents())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getTenantId()).isEqualTo(tenant);
              assertThat(e.getSessionId()).isEqualTo(session);
              assertThat(e.getPatientId()).isEqualTo(patient);
              assertThat(e.getPractitionerId()).isEqualTo(practitioner);
              assertThat(e.getKind()).isEqualTo("session");
              assertThat(e.getModality()).isEqualTo("video");
              assertThat(e.getOccurredFrom()).isEqualTo(FROM);
              assertThat(e.getClosedAt()).isEqualTo(NOW);
              assertThat(e.getDurationMin()).isEqualTo(45);
            });
  }

  @Test
  void closeWithoutDurationPublishesTheFallbackDuration() {
    when(repo.session(session))
        .thenReturn(Optional.of(new SessionDetail("session", "in_person", null, null)));

    service.close(session, new CloseSessionCommand(null, null));

    assertThat(closedEvents())
        .singleElement()
        .extracting(SessionClosed::getDurationMin)
        .isEqualTo(SessionService.FALLBACK_DURATION_MIN);
  }

  @Test
  void closeOfAnAlreadyClosedSessionPublishesNothing() {
    when(repo.session(session))
        .thenReturn(
            Optional.of(
                new SessionDetail("session", "in_person", 30, NOW.minus(Duration.ofDays(1)))));

    assertThatThrownBy(() -> service.close(session, new CloseSessionCommand(30, null)))
        .isInstanceOfSatisfying(
            ClinicalException.class,
            e -> assertThat(e.error()).isEqualTo(ClinicalError.SESSION_ALREADY_CLOSED));
    assertThat(published).isEmpty();
  }

  @Test
  void closeOfAnArchivedSessionPublishesNothing() {
    when(repo.findForUpdate(session)).thenReturn(Optional.of(sessionEntry(FROM)));

    assertThatThrownBy(() -> service.close(session, new CloseSessionCommand(30, null)))
        .isInstanceOf(ClinicalException.class);
    assertThat(published).isEmpty();
  }

  @Test
  void contactPublishesSessionClosedAtItsCreation() {
    when(entries.write(
            eq(tenant), eq(practitioner), eq(patient), eq(EntryType.SESSION), any(), any(), any()))
        .thenReturn(session);

    service.contact(patient, new ContactCommand("phone", FROM, "Appel", 15));

    assertThat(closedEvents())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getSessionId()).isEqualTo(session);
              assertThat(e.getKind()).isEqualTo("contact");
              assertThat(e.getModality()).isEqualTo("phone");
              assertThat(e.getOccurredFrom()).isEqualTo(FROM);
              assertThat(e.getClosedAt()).isEqualTo(NOW);
              assertThat(e.getDurationMin()).isEqualTo(15);
            });
  }

  @Test
  void refusedContactPublishesNothing() {
    assertThatThrownBy(
            () -> service.contact(patient, new ContactCommand("in_person", FROM, "Appel", null)))
        .isInstanceOf(ClinicalException.class);
    assertThat(published).isEmpty();
  }

  @Test
  void closeStillDelegatesTheCorrectionThatPublishesEntryCorrected() {
    when(repo.session(session))
        .thenReturn(Optional.of(new SessionDetail("session", "video", null, null)));

    service.close(session, new CloseSessionCommand(45, null));

    verify(entries).correct(eq(session), any(), anyBoolean());
  }
}
