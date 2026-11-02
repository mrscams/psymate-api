package com.psymate.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Curseur keyset de la timeline : aller-retour sans perte de précision, rejet des illisibles. */
class TimelinePaginationTest {

  private static String b64(String raw) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void roundTripKeepsMicrosecondPrecision() {
    Instant micro = Instant.parse("2026-10-20T19:00:00.123456Z");
    UUID id = UUID.randomUUID();

    String encoded = new TimelineCursor(micro, id).encode();
    TimelineCursor decoded = TimelineCursor.decode(encoded);

    assertThat(decoded.occurredFrom()).isEqualTo(micro);
    assertThat(decoded.occurredFrom().getNano()).isEqualTo(123_456_000);
    assertThat(decoded.id()).isEqualTo(id);
  }

  @Test
  void roundTripKeepsWholeSecondsAndNanoseconds() {
    UUID id = UUID.randomUUID();
    Instant whole = Instant.parse("2019-05-04T00:00:00Z");
    Instant nanos = Instant.parse("2019-05-04T00:00:00.000000001Z");

    assertThat(TimelineCursor.decode(new TimelineCursor(whole, id).encode()))
        .isEqualTo(new TimelineCursor(whole, id));
    assertThat(TimelineCursor.decode(new TimelineCursor(nanos, id).encode()))
        .isEqualTo(new TimelineCursor(nanos, id));
  }

  @Test
  void encodedCursorIsUrlSafeWithoutPadding() {
    String encoded =
        new TimelineCursor(Instant.parse("2026-10-20T19:00:00.5Z"), UUID.randomUUID()).encode();

    assertThat(encoded).doesNotContain("=", "+", "/").matches("[A-Za-z0-9_-]+");
  }

  @Test
  void differentKeysGiveDifferentCursors() {
    Instant at = Instant.parse("2026-10-20T19:00:00Z");

    assertThat(new TimelineCursor(at, UUID.randomUUID()).encode())
        .isNotEqualTo(new TimelineCursor(at, UUID.randomUUID()).encode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"!!!", "not base64 at all", "a", "====", "ab cd"})
  void invalidBase64IsRejected(String garbage) {
    assertThatThrownBy(() -> TimelineCursor.decode(garbage))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingSeparatorIsRejected() {
    assertThatThrownBy(() -> TimelineCursor.decode(b64("2026-10-20T19:00:00Z" + UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidUuidIsRejected() {
    assertThatThrownBy(() -> TimelineCursor.decode(b64("2026-10-20T19:00:00Z|not-a-uuid")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                TimelineCursor.decode(
                    b64("2026-10-20T19:00:00Z|zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidInstantIsRejected() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> TimelineCursor.decode(b64("hier|" + id)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TimelineCursor.decode(b64("2026-13-45T99:00:00Z|" + id)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TimelineCursor.decode(b64("|" + id)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void emptyOrNullCursorIsRejected() {
    assertThatThrownBy(() -> TimelineCursor.decode(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TimelineCursor.decode(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
