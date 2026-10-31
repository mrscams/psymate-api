package com.psymate.identity.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

class PurgeJobTest {

  @Test
  void cronFiresOnceADayAt0315Utc() {
    CronExpression cron = CronExpression.parse(PurgeJob.CRON);
    ZonedDateTime start = ZonedDateTime.of(2026, 10, 31, 0, 0, 0, 0, ZoneOffset.UTC);
    ZonedDateTime first = cron.next(start);
    assertThat(first).isEqualTo(ZonedDateTime.of(2026, 10, 31, 3, 15, 0, 0, ZoneOffset.UTC));
    assertThat(cron.next(first))
        .isEqualTo(ZonedDateTime.of(2026, 11, 1, 3, 15, 0, 0, ZoneOffset.UTC));
  }
}
