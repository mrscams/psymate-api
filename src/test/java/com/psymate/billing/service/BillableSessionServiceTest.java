package com.psymate.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Règles pures des séances à facturer : jour d'une séance (Europe/Paris), kind facturable. */
class BillableSessionServiceTest {

  @Test
  void theDayOfASessionIsTakenInEuropeParis() {
    // Hiver (UTC+1) : 23 h 30 UTC est déjà le lendemain à Paris.
    assertThat(BillableSessionService.occurredOn(Instant.parse("2026-11-27T23:30:00Z")))
        .isEqualTo(LocalDate.of(2026, 11, 28));
    assertThat(BillableSessionService.occurredOn(Instant.parse("2026-11-27T22:59:59Z")))
        .isEqualTo(LocalDate.of(2026, 11, 27));
    // Été (UTC+2) : 22 h 00 UTC est minuit à Paris.
    assertThat(BillableSessionService.occurredOn(Instant.parse("2026-07-14T22:00:00Z")))
        .isEqualTo(LocalDate.of(2026, 7, 15));
    assertThat(BillableSessionService.occurredOn(Instant.parse("2026-07-14T21:59:59Z")))
        .isEqualTo(LocalDate.of(2026, 7, 14));
  }

  @Test
  void onlyTheSessionKindIsBillable() {
    assertThat(BillableSessionService.billableKind("session")).isTrue();
    assertThat(BillableSessionService.billableKind("intake")).isFalse();
    assertThat(BillableSessionService.billableKind("contact")).isFalse();
    assertThat(BillableSessionService.billableKind(null)).isFalse();
  }
}
