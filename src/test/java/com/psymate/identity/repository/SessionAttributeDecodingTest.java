package com.psymate.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M10-06 : lecture en SQL d'un attribut de session sérialisé par Spring Session (JDK). Le filtre
 * doit refuser toute autre classe avant de l'instancier : la preuve est une classe sentinelle dont
 * la désérialisation lève un drapeau.
 */
class SessionAttributeDecodingTest {

  /** Lève un drapeau statique dès qu'une instance est désérialisée. */
  static final class Sentinel implements Serializable {
    private static final long serialVersionUID = 1L;
    static boolean deserialized;

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      deserialized = true;
    }

    private Object readResolve() {
      deserialized = true;
      return this;
    }
  }

  @BeforeEach
  void resetSentinel() {
    Sentinel.deserialized = false;
  }

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
  void ourTwoAttributesFitWellUnderTheSizeLimit() throws Exception {
    assertThat(serialize("Samsung Internet · ChromeOS").length).isLessThan(100);
    assertThat(serialize("2026-11-03T14:00:00.123456789Z").length).isLessThan(100);
  }

  @Test
  void neverInstantiatesAClassOtherThanString() throws Exception {
    assertThat(SessionNativeQueriesImpl.stringAttribute(serialize(new Sentinel()))).isNull();
    // Le filtre a refusé la classe avant toute instanciation : ni readObject ni readResolve.
    assertThat(Sentinel.deserialized).isFalse();
  }

  @Test
  void refusesNumbersCollectionsAndArrays() throws Exception {
    assertThat(SessionNativeQueriesImpl.stringAttribute(serialize(Integer.valueOf(7)))).isNull();
    assertThat(SessionNativeQueriesImpl.stringAttribute(serialize(new ArrayList<String>())))
        .isNull();
    assertThat(SessionNativeQueriesImpl.stringAttribute(serialize(new String[] {"a", "b"})))
        .isNull();
    assertThat(SessionNativeQueriesImpl.stringAttribute(serialize(new int[] {1, 2, 3}))).isNull();
    // Un tableau d'objets sentinelles est refusé lui aussi, sans instancier un seul élément.
    assertThat(SessionNativeQueriesImpl.stringAttribute(serialize(new Sentinel[] {new Sentinel()})))
        .isNull();
    assertThat(Sentinel.deserialized).isFalse();
  }

  @Test
  void refusesAStringLongerThanTheByteLimit() throws Exception {
    assertThat(SessionNativeQueriesImpl.stringAttribute(serialize("x".repeat(5_000)))).isNull();
  }

  @Test
  void returnsNullForAbsentOrCorruptBytes() {
    assertThat(SessionNativeQueriesImpl.stringAttribute(null)).isNull();
    assertThat(SessionNativeQueriesImpl.stringAttribute(new byte[] {1, 2, 3})).isNull();
    assertThat(SessionNativeQueriesImpl.stringAttribute(new byte[0])).isNull();
  }
}
