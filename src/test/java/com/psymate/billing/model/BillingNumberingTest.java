package com.psymate.billing.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Numérotation continue par année de la date d'émission (G Q89). */
class BillingNumberingTest {

  @Test
  void theSeriesRestartsAt0001WhenTheYearOfTheIssueDateChanges() {
    // Compteurs par année, comme billing_counter pour un praticien et une nature.
    Map<Integer, Integer> counters = new HashMap<>();
    assertThat(issue(counters, LocalDate.of(2026, 12, 30))).isEqualTo("F-2026-0001");
    assertThat(issue(counters, LocalDate.of(2026, 12, 31))).isEqualTo("F-2026-0002");
    assertThat(issue(counters, LocalDate.of(2027, 1, 1))).isEqualTo("F-2027-0001");
    // Émise en janvier mais datée de décembre : la suite de décembre continue.
    assertThat(issue(counters, LocalDate.of(2026, 12, 31))).isEqualTo("F-2026-0003");
    assertThat(issue(counters, LocalDate.of(2027, 1, 2))).isEqualTo("F-2027-0002");
    assertThat(counters).containsEntry(2026, 3).containsEntry(2027, 2);
  }

  @Test
  void theNumberHasFourDigitsAtLeast() {
    assertThat(BillingNumbering.next("F", LocalDate.of(2026, 5, 1), 0)).isEqualTo("F-2026-0001");
    assertThat(BillingNumbering.next("AV", LocalDate.of(2026, 5, 1), 41)).isEqualTo("AV-2026-0042");
    assertThat(BillingNumbering.next("F", LocalDate.of(2026, 5, 1), 9999))
        .isEqualTo("F-2026-10000");
    assertThat(BillingNumbering.yearOf(LocalDate.of(2027, 1, 1))).isEqualTo(2027);
  }

  /** Émission d'un document daté {@code issueDate} : numéro suivant de la suite de son année. */
  private static String issue(Map<Integer, Integer> counters, LocalDate issueDate) {
    int year = BillingNumbering.yearOf(issueDate);
    int last = counters.getOrDefault(year, 0);
    String number = BillingNumbering.next("F", issueDate, last);
    counters.put(year, last + 1);
    return number;
  }
}
