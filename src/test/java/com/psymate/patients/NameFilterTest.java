package com.psymate.patients;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameFilterTest {

  @Test
  void blankOrNullMeansNoFilter() {
    assertThat(NameFilter.pattern(null)).isNull();
    assertThat(NameFilter.pattern("   ")).isNull();
  }

  @Test
  void wrapsTrimmedTextInContainsPattern() {
    assertThat(NameFilter.pattern("  Marie   D ")).isEqualTo("%Marie D%");
  }

  @Test
  void keepsAccentsForSqlUnaccentToHandle() {
    assertThat(NameFilter.pattern("Éloïse")).isEqualTo("%Éloïse%");
  }

  @Test
  void escapesLikeWildcards() {
    assertThat(NameFilter.pattern("100%_a\\b")).isEqualTo("%100\\%\\_a\\\\b%");
  }
}
