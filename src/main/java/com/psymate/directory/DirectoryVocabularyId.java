package com.psymate.directory;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Clé composite de {@code directory_vocabulary} : (kind, code). */
@Embeddable
class DirectoryVocabularyId implements Serializable {

  private static final long serialVersionUID = 1L;

  private String kind;
  private String code;

  protected DirectoryVocabularyId() {}

  DirectoryVocabularyId(String kind, String code) {
    this.kind = Objects.requireNonNull(kind);
    this.code = Objects.requireNonNull(code);
  }

  String getKind() {
    return kind;
  }

  String getCode() {
    return code;
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof DirectoryVocabularyId other
            && Objects.equals(kind, other.kind)
            && Objects.equals(code, other.code));
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, code);
  }
}
