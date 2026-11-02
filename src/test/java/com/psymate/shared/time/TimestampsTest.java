package com.psymate.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimestampsTest {

  private static final Instant NANOS = Instant.parse("2026-11-02T10:15:30.123456789Z");

  @Test
  void utcTruncatesToMicrosecondsWithoutRounding() {
    OffsetDateTime t = Timestamps.utc(NANOS);
    assertThat(t.getOffset()).isEqualTo(ZoneOffset.UTC);
    // .123456789 devient .123456 (troncature, pas arrondi à .123457).
    assertThat(t.getNano()).isEqualTo(123_456_000);
    assertThat(Timestamps.utc(Instant.parse("2026-11-02T10:15:30.999999999Z")))
        .isEqualTo(OffsetDateTime.parse("2026-11-02T10:15:30.999999Z"));
    assertThat(Timestamps.utc(null)).isNull();
  }

  @Test
  void instantKeepsTheMomentAndNull() {
    OffsetDateTime paris = OffsetDateTime.parse("2026-11-02T11:15:30.123456+01:00");
    assertThat(Timestamps.instant(paris)).isEqualTo(Instant.parse("2026-11-02T10:15:30.123456Z"));
    assertThat(Timestamps.instant(null)).isNull();
  }

  @Test
  void nowIsUtcAndTruncatedWhateverTheClockZone() {
    Clock paris = Clock.fixed(NANOS, ZoneId.of("Europe/Paris"));
    OffsetDateTime t = Timestamps.now(paris);
    assertThat(t).isEqualTo(OffsetDateTime.parse("2026-11-02T10:15:30.123456Z"));
    assertThat(t.getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(Timestamps.now().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(Timestamps.now().getNano() % 1_000).isZero();
  }

  @Test
  void microsKeepsTheOffset() {
    OffsetDateTime paris = OffsetDateTime.parse("2026-11-02T11:15:30.123456789+01:00");
    assertThat(Timestamps.micros(paris))
        .isEqualTo(OffsetDateTime.parse("2026-11-02T11:15:30.123456+01:00"));
    assertThat(Timestamps.micros(null)).isNull();
  }
}
