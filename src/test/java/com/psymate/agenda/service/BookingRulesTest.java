package com.psymate.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.agenda.model.AppointmentStatus;
import com.psymate.agenda.service.BookingRules.PublicStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Règles pures de la prise de rendez-vous en ligne (M14-06) : jetons, empreintes, états. */
class BookingRulesTest {

  private static final byte[] SECRET = "secret-de-test".getBytes(StandardCharsets.UTF_8);
  private static final Instant NOW = Instant.parse("2026-12-01T10:00:00Z");

  // ---- jetons et empreintes

  @Test
  void tokenIs256RandomBitsInBase64Url() {
    Set<String> tokens = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      String token = BookingRules.newToken();
      assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
      tokens.add(token);
    }
    assertThat(tokens).hasSize(100);
  }

  @Test
  void tokenHashIsHexSha256() {
    assertThat(BookingRules.sha256("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    String token = BookingRules.newToken();
    assertThat(BookingRules.sha256(token)).hasSize(64).doesNotContain(token);
  }

  @Test
  void emailIsNormalizedToLowerCaseWithoutBlanks() {
    assertThat(BookingRules.normalizeEmail("  Sara.Benali@Example.TEST \t"))
        .isEqualTo("sara.benali@example.test");
    assertThat(BookingRules.normalizeEmail("sara benali@example.test "))
        .isEqualTo("sarabenali@example.test");
  }

  @Test
  void emailHashIgnoresCaseAndBlanksAndDependsOnTheSecret() {
    String hash = BookingRules.emailHash("sara@example.test", SECRET);
    assertThat(hash).matches("[0-9a-f]{64}").doesNotContain("sara");
    assertThat(BookingRules.emailHash(" SARA@Example.test ", SECRET)).isEqualTo(hash);
    assertThat(BookingRules.emailHash("sara2@example.test", SECRET)).isNotEqualTo(hash);
    assertThat(
            BookingRules.emailHash(
                "sara@example.test", "autre-secret".getBytes(StandardCharsets.UTF_8)))
        .isNotEqualTo(hash);
    // Une empreinte avec secret n'est pas le SHA-256 nu de l'adresse.
    assertThat(hash).isNotEqualTo(BookingRules.sha256("sara@example.test"));
  }

  @Test
  void sameEmailIgnoresCaseAndBlanksAndNeverMatchesNull() {
    assertThat(BookingRules.sameEmail("Sara@Example.test", " sara@example.TEST")).isTrue();
    assertThat(BookingRules.sameEmail("sara@example.test", "sara2@example.test")).isFalse();
    assertThat(BookingRules.sameEmail(null, "sara@example.test")).isFalse();
    assertThat(BookingRules.sameEmail("sara@example.test", null)).isFalse();
  }

  // ---- anti-spam et plafond

  @Test
  void honeypotTooFastFutureOrMissingFormTimeLooksLikeSpam() {
    assertThat(BookingRules.looksLikeSpam("", NOW.minusSeconds(30), NOW)).isFalse();
    assertThat(BookingRules.looksLikeSpam(null, NOW.minusSeconds(3), NOW)).isFalse();
    assertThat(BookingRules.looksLikeSpam("x", NOW.minusSeconds(30), NOW)).isTrue();
    assertThat(BookingRules.looksLikeSpam("  ", NOW.minusSeconds(30), NOW)).isFalse();
    assertThat(BookingRules.looksLikeSpam(null, NOW.minusSeconds(1), NOW)).isTrue();
    assertThat(BookingRules.looksLikeSpam(null, NOW.minusMillis(2999), NOW)).isTrue();
    assertThat(BookingRules.looksLikeSpam(null, NOW.plusSeconds(5), NOW)).isTrue();
    assertThat(BookingRules.looksLikeSpam(null, null, NOW)).isTrue();
  }

  @Test
  void addressCapIsReachedAtThreeRequestsInTheHour() {
    assertThat(BookingRules.emailCapReached(0)).isFalse();
    assertThat(BookingRules.emailCapReached(2)).isFalse();
    assertThat(BookingRules.emailCapReached(3)).isTrue();
    assertThat(BookingRules.emailCapReached(10)).isTrue();
  }

  // ---- état public

  @Test
  void publicStatusFollowsTheAppointmentStatusAndItsEnd() {
    Instant future = NOW.plusSeconds(3600);
    Instant past = NOW.minusSeconds(3600);
    assertThat(BookingRules.publicStatus(AppointmentStatus.tentative, future, NOW))
        .isEqualTo(PublicStatus.pending);
    assertThat(BookingRules.publicStatus(AppointmentStatus.tentative, past, NOW))
        .isEqualTo(PublicStatus.pending);
    assertThat(BookingRules.publicStatus(AppointmentStatus.planned, future, NOW))
        .isEqualTo(PublicStatus.confirmed);
    assertThat(BookingRules.publicStatus(AppointmentStatus.planned, past, NOW))
        .isEqualTo(PublicStatus.past);
    assertThat(BookingRules.publicStatus(AppointmentStatus.planned, NOW, NOW))
        .isEqualTo(PublicStatus.past);
    assertThat(BookingRules.publicStatus(AppointmentStatus.done, future, NOW))
        .isEqualTo(PublicStatus.past);
    assertThat(BookingRules.publicStatus(AppointmentStatus.cancelled, future, NOW))
        .isEqualTo(PublicStatus.cancelled);
  }

  @Test
  void onlyAnActiveAppointmentThatHasNotStartedCanBeCancelled() {
    Instant future = NOW.plusSeconds(60);
    Instant started = NOW.minusSeconds(60);
    assertThat(BookingRules.canCancel(AppointmentStatus.tentative, future, NOW)).isTrue();
    assertThat(BookingRules.canCancel(AppointmentStatus.planned, future, NOW)).isTrue();
    assertThat(BookingRules.canCancel(AppointmentStatus.planned, started, NOW)).isFalse();
    assertThat(BookingRules.canCancel(AppointmentStatus.planned, NOW, NOW)).isFalse();
    assertThat(BookingRules.canCancel(AppointmentStatus.cancelled, future, NOW)).isFalse();
    assertThat(BookingRules.canCancel(AppointmentStatus.done, future, NOW)).isFalse();
  }

  // ---- e-mails

  @Test
  void mailValuesStayOnOneLineAndLocaleDefaultsToFrench() {
    assertThat(BookingMailer.singleLine(" Sara\r\nBcc: x@example.test "))
        .isEqualTo("Sara Bcc: x@example.test");
    assertThat(BookingMailer.singleLine(null)).isEmpty();
    assertThat(BookingMailer.localeOf("en").getLanguage()).isEqualTo("en");
    assertThat(BookingMailer.localeOf("fr").getLanguage()).isEqualTo("fr");
    assertThat(BookingMailer.localeOf(null).getLanguage()).isEqualTo("fr");
  }
}
