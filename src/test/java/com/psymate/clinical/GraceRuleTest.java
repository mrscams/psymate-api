package com.psymate.clinical;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.clinical.domain.Entry;
import com.psymate.clinical.domain.EntryStatus;
import com.psymate.clinical.domain.EntryType;
import com.psymate.clinical.domain.GraceRule;
import com.psymate.clinical.domain.TimeSpan;
import com.psymate.clinical.domain.Visibility;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraceRuleTest {

  private static final Instant CREATED = Instant.parse("2026-10-30T10:00:00Z");
  private static final UUID AUTHOR = UUID.randomUUID();
  private static final GraceRule RULE = new GraceRule(Duration.ofMinutes(5));

  private static Entry entry(int linkCount) {
    return new Entry(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        EntryType.NOTE,
        EntryStatus.FINAL,
        Visibility.CLINICAL,
        new TimeSpan(CREATED, CREATED, TimeSpan.Precision.EXACT, null),
        UUID.randomUUID(),
        UUID.randomUUID(),
        1,
        linkCount,
        "practitioner",
        null,
        AUTHOR,
        CREATED,
        CREATED);
  }

  @Test
  void appliesForSameAuthorWithinWindowWithoutLinkOrVersion() {
    assertThat(RULE.allows(entry(0), AUTHOR, 1, CREATED.plusSeconds(299))).isTrue();
  }

  @Test
  void doesNotApplyAtOrAfterFiveMinutes() {
    assertThat(RULE.allows(entry(0), AUTHOR, 1, CREATED.plus(Duration.ofMinutes(5)))).isFalse();
    assertThat(RULE.allows(entry(0), AUTHOR, 1, CREATED.plus(Duration.ofHours(1)))).isFalse();
  }

  @Test
  void doesNotApplyForAnotherAuthor() {
    assertThat(RULE.allows(entry(0), UUID.randomUUID(), 1, CREATED.plusSeconds(10))).isFalse();
    assertThat(RULE.allows(entry(0), null, 1, CREATED.plusSeconds(10))).isFalse();
  }

  @Test
  void doesNotApplyOnceLinked() {
    assertThat(RULE.allows(entry(1), AUTHOR, 1, CREATED.plusSeconds(10))).isFalse();
  }

  @Test
  void doesNotApplyOnceAnotherVersionExists() {
    assertThat(RULE.allows(entry(0), AUTHOR, 2, CREATED.plusSeconds(10))).isFalse();
  }

  @Test
  void zeroOrNegativePeriodDisablesTheRule() {
    assertThat(new GraceRule(Duration.ZERO).allows(entry(0), AUTHOR, 1, CREATED)).isFalse();
    assertThat(new GraceRule(Duration.ofMinutes(-1)).allows(entry(0), AUTHOR, 1, CREATED))
        .isFalse();
  }
}
