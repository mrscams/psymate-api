package com.psymate.billing.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Curseur opaque de la liste des factures. */
class InvoiceCursorTest {

  @Test
  void aCursorRoundTrips() {
    InvoiceCursor cursor = new InvoiceCursor(1, LocalDate.of(2026, 11, 5), UUID.randomUUID());
    assertThat(InvoiceCursor.decode(cursor.encode())).isEqualTo(cursor);
    assertThat(cursor.encode()).doesNotContain("|").doesNotContain("=");
  }

  @Test
  void anUnreadableCursorIsRefused() {
    assertThatThrownBy(() -> InvoiceCursor.decode("pas un curseur !"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> InvoiceCursor.decode(encode("2|2026-11-05|" + UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> InvoiceCursor.decode(encode("1|2026-13-05|" + UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> InvoiceCursor.decode(encode("1|2026-11-05|x")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> InvoiceCursor.decode(encode("1|2026-11-05")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static String encode(String raw) {
    return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
