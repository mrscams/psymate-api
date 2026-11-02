package com.psymate.overview.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bornes et dates de points des trois fenêtres (logique pure, sans base). */
class OverviewWindowTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

  @Test
  void parsesTheThreeCodesAndRejectsTheRest() {
    assertThat(OverviewWindow.parse("30d")).contains(OverviewWindow.D30);
    assertThat(OverviewWindow.parse("90d")).contains(OverviewWindow.D90);
    assertThat(OverviewWindow.parse("12m")).contains(OverviewWindow.M12);
    assertThat(OverviewWindow.parse("7d")).isEmpty();
    assertThat(OverviewWindow.parse("")).isEmpty();
    assertThat(OverviewWindow.parse("90D")).isEmpty();
    assertThat(OverviewWindow.DEFAULT).isEqualTo(OverviewWindow.D90);
  }

  @Test
  void thirtyDaysGivesOnePointPerDayEndingToday() {
    List<LocalDate> dates = OverviewWindow.D30.pointDates(TODAY);

    assertThat(dates).hasSize(30);
    assertThat(dates.get(0)).isEqualTo(TODAY.minusDays(29));
    assertThat(dates.get(29)).isEqualTo(TODAY);
    assertThat(dates).isSorted();
  }

  @Test
  void ninetyDaysGivesNinetyDailyPoints() {
    List<LocalDate> dates = OverviewWindow.D90.pointDates(TODAY);

    assertThat(dates).hasSize(90).isSorted();
    assertThat(dates.get(0)).isEqualTo(TODAY.minusDays(89));
    assertThat(dates.get(89)).isEqualTo(TODAY);
  }

  @Test
  void twelveMonthsGivesOneWeeklyPointNeverMoreThanTheLimit() {
    List<LocalDate> dates = OverviewWindow.M12.pointDates(TODAY);

    assertThat(dates.size()).isBetween(52, 53).isLessThanOrEqualTo(OverviewWindow.MAX_POINTS);
    assertThat(dates.get(dates.size() - 1)).isEqualTo(TODAY);
    for (int i = 1; i < dates.size(); i++) {
      assertThat(dates.get(i).toEpochDay() - dates.get(i - 1).toEpochDay()).isEqualTo(7);
    }
    assertThat(dates.get(0)).isAfterOrEqualTo(OverviewWindow.M12.start(TODAY));
  }

  @Test
  void everyWindowStaysWithinThePointLimit() {
    for (OverviewWindow w : OverviewWindow.values()) {
      assertThat(w.pointDates(TODAY).size()).as(w.code()).isLessThanOrEqualTo(90);
    }
  }

  @Test
  void previousWindowIsAdjacentAndOfTheSameLength() {
    assertThat(OverviewWindow.D30.previousStart(TODAY))
        .isEqualTo(OverviewWindow.D30.start(TODAY).minusDays(30));
    assertThat(OverviewWindow.D90.previousStart(TODAY))
        .isEqualTo(OverviewWindow.D90.start(TODAY).minusDays(90));
    assertThat(OverviewWindow.M12.start(TODAY)).isEqualTo(LocalDate.of(2025, 11, 1));
    assertThat(OverviewWindow.M12.previousStart(TODAY)).isEqualTo(LocalDate.of(2024, 11, 1));
  }

  @Test
  void earliestNeededCoversThePreviousWindowAndTheRollingDays() {
    assertThat(OverviewWindow.earliestNeeded(TODAY))
        .isEqualTo(OverviewWindow.M12.previousStart(TODAY).minusDays(7));
  }
}
