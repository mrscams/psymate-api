package com.psymate.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ContactRequestRulesTest {

  private static final Instant NOW = Instant.parse("2026-01-10T10:00:00Z");
  private static final LocalDate DAY = LocalDate.of(2026, 1, 10);

  private static String key(int fill) {
    byte[] k = new byte[32];
    java.util.Arrays.fill(k, (byte) fill);
    return Base64.getEncoder().encodeToString(k);
  }

  @Test
  void honeypotMakesTheMessageSpam() {
    assertThat(ContactRequestService.looksLikeSpam("x", NOW.minusSeconds(60), NOW)).isTrue();
    assertThat(ContactRequestService.looksLikeSpam("  ", NOW.minusSeconds(60), NOW)).isFalse();
    assertThat(ContactRequestService.looksLikeSpam(null, NOW.minusSeconds(60), NOW)).isFalse();
  }

  @Test
  void minimalDelayIsThreeSeconds() {
    assertThat(ContactRequestService.looksLikeSpam("", NOW.minusMillis(2999), NOW)).isTrue();
    assertThat(ContactRequestService.looksLikeSpam("", NOW.minusSeconds(3), NOW)).isFalse();
    assertThat(ContactRequestService.looksLikeSpam("", NOW.plusSeconds(1), NOW)).isTrue();
    assertThat(ContactRequestService.looksLikeSpam("", null, NOW)).isTrue();
  }

  @Test
  void relayErrorIsTruncatedTo500Characters() {
    assertThat(ContactRequestService.truncate("x".repeat(900))).hasSize(500);
    assertThat(ContactRequestService.truncate("court")).isEqualTo("court");
  }

  @Test
  void ipHashChangesEveryDayAndNeverContainsTheIp() {
    IpHasher hasher = new IpHasher(key(1));
    String today = hasher.hash("203.0.113.7", DAY);
    assertThat(today).matches("[0-9a-f]{64}").doesNotContain("203.0.113.7");
    assertThat(hasher.hash("203.0.113.7", DAY)).isEqualTo(today);
    assertThat(hasher.hash("203.0.113.7", DAY.plusDays(1))).isNotEqualTo(today);
    assertThat(hasher.hash("203.0.113.8", DAY)).isNotEqualTo(today);
    assertThat(new IpHasher(key(2)).hash("203.0.113.7", DAY)).isNotEqualTo(today);
  }

  @Test
  void ipHasherRejectsAWrongKeySize() {
    assertThatThrownBy(() -> new IpHasher(Base64.getEncoder().encodeToString(new byte[8])))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void singleLineStripsControlCharacters() {
    assertThat(ContactMailer.singleLine("a\r\nBcc: x")).doesNotContain("\n").doesNotContain("\r");
  }
}
