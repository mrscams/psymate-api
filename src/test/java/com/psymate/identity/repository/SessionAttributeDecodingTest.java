package com.psymate.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/** M10-06 : lecture en SQL d'un attribut de session sérialisé par Spring Session (JDK). */
class SessionAttributeDecodingTest {

  private static byte[] serialize(Object value) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ObjectOutputStream stream = new ObjectOutputStream(out)) {
      stream.writeObject(value);
    }
    return out.toByteArray();
  }

  @Test
  void readsASerializedStringWithAccents() throws Exception {
    assertThat(SessionNativeQueriesImpl.stringAttribute(serialize("Chrome · Windows")))
        .isEqualTo("Chrome · Windows");
    assertThat(SessionNativeQueriesImpl.stringAttribute(serialize("2026-11-03T14:00:00.123456Z")))
        .isEqualTo("2026-11-03T14:00:00.123456Z");
  }

  @Test
  void refusesAnyClassOtherThanString() throws Exception {
    assertThat(SessionNativeQueriesImpl.stringAttribute(serialize(new ArrayList<String>())))
        .isNull();
    assertThat(SessionNativeQueriesImpl.stringAttribute(serialize(new Marker()))).isNull();
  }

  @Test
  void returnsNullForAbsentOrCorruptBytes() {
    assertThat(SessionNativeQueriesImpl.stringAttribute(null)).isNull();
    assertThat(SessionNativeQueriesImpl.stringAttribute(new byte[] {1, 2, 3})).isNull();
    assertThat(SessionNativeQueriesImpl.stringAttribute(new byte[0])).isNull();
  }

  private record Marker() implements Serializable {}
}
