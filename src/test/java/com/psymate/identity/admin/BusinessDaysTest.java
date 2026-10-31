package com.psymate.identity.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BusinessDaysTest {

  // 2026-11-02 est un lundi.

  @Test
  void fridayPlusTwoBusinessDaysIsTuesdaySameTime() {
    Instant friday = Instant.parse("2026-10-30T17:00:00Z");
    assertThat(BusinessDays.addBusinessDays(friday, 2))
        .isEqualTo(Instant.parse("2026-11-03T17:00:00Z"));
  }

  @Test
  void saturdayPlusTwoBusinessDaysIsTuesday() {
    Instant saturday = Instant.parse("2026-10-31T09:30:00Z");
    assertThat(BusinessDays.addBusinessDays(saturday, 2))
        .isEqualTo(Instant.parse("2026-11-03T09:30:00Z"));
  }

  @Test
  void wednesdayPlusTwoBusinessDaysIsFriday() {
    Instant wednesday = Instant.parse("2026-10-28T08:00:00Z");
    assertThat(BusinessDays.addBusinessDays(wednesday, 2))
        .isEqualTo(Instant.parse("2026-10-30T08:00:00Z"));
  }

  @Test
  void zeroDaysIsUnchanged() {
    Instant any = Instant.parse("2026-10-31T09:30:00Z");
    assertThat(BusinessDays.addBusinessDays(any, 0)).isEqualTo(any);
  }
}
