package com.psymate.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.BookingConfirmation;
import com.psymate.agenda.service.BookingSettings.Window;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Lecture typée des huit clés booking.* (G Q75), défauts si une clé manque ou est illisible. */
class BookingSettingsReaderTest {

  private static Map<String, Object> complete() {
    Map<String, Object> p = new HashMap<>();
    p.put("booking.enabled", true);
    p.put("booking.confirmation", "auto");
    p.put(
        "booking.windows",
        List.of(
            Map.of("day", 3, "start", "14:00", "end", "18:00"),
            Map.of("day", 1, "start", "09:00", "end", "12:30")));
    p.put("booking.slotDurationMin", 45);
    p.put("booking.minNoticeHours", 48);
    p.put("booking.horizonDays", 14);
    p.put("booking.modalities", List.of("phone", "in_person"));
    p.put("booking.timezone", "America/Montreal");
    p.put("theme", "dark");
    return p;
  }

  @Test
  void readsTheEightKeysTyped() {
    BookingSettings s = BookingSettingsReader.of(complete());

    assertThat(s.enabled()).isTrue();
    assertThat(s.confirmation()).isEqualTo(BookingConfirmation.auto);
    assertThat(s.windows())
        .containsExactly(
            new Window(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 30)),
            new Window(DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), LocalTime.of(18, 0)));
    assertThat(s.slotDurationMin()).isEqualTo(45);
    assertThat(s.minNoticeHours()).isEqualTo(48);
    assertThat(s.horizonDays()).isEqualTo(14);
    assertThat(s.modalities())
        .containsExactly(AppointmentModality.in_person, AppointmentModality.phone);
    assertThat(s.timezone()).isEqualTo(ZoneId.of("America/Montreal"));
  }

  @Test
  void missingKeysTakeTheDefaultsOfQ75() {
    assertThat(BookingSettingsReader.of(Map.of())).isEqualTo(BookingSettings.defaults());
    assertThat(BookingSettingsReader.of(null)).isEqualTo(BookingSettings.defaults());

    BookingSettings d = BookingSettings.defaults();
    assertThat(d.enabled()).isFalse();
    assertThat(d.confirmation()).isEqualTo(BookingConfirmation.manual);
    assertThat(d.windows()).isEmpty();
    assertThat(d.slotDurationMin()).isEqualTo(60);
    assertThat(d.minNoticeHours()).isEqualTo(24);
    assertThat(d.horizonDays()).isEqualTo(28);
    assertThat(d.modalities()).containsExactly(AppointmentModality.in_person);
    assertThat(d.timezone()).isEqualTo(ZoneId.of("Europe/Paris"));
  }

  @Test
  void unreadableValuesTakeTheirDefault() {
    Map<String, Object> p = complete();
    p.put("booking.enabled", "true");
    p.put("booking.confirmation", "always");
    p.put("booking.slotDurationMin", 7);
    p.put("booking.minNoticeHours", 500);
    p.put("booking.horizonDays", "14");
    p.put("booking.modalities", List.of("in_person", "home"));
    p.put("booking.timezone", "Mars/Olympus");

    BookingSettings s = BookingSettingsReader.of(p);

    assertThat(s.enabled()).isFalse();
    assertThat(s.confirmation()).isEqualTo(BookingConfirmation.manual);
    assertThat(s.slotDurationMin()).isEqualTo(60);
    assertThat(s.minNoticeHours()).isEqualTo(24);
    assertThat(s.horizonDays()).isEqualTo(28);
    assertThat(s.modalities()).containsExactly(AppointmentModality.in_person);
    assertThat(s.timezone()).isEqualTo(ZoneId.of("Europe/Paris"));
    // Les autres clés restent lues.
    assertThat(s.windows()).hasSize(2);
  }

  @Test
  void slotDurationMustBeAMultipleOfFive() {
    Map<String, Object> p = complete();
    p.put("booking.slotDurationMin", 47);
    assertThat(BookingSettingsReader.of(p).slotDurationMin()).isEqualTo(60);
    p.put("booking.slotDurationMin", 180L);
    assertThat(BookingSettingsReader.of(p).slotDurationMin()).isEqualTo(180);
  }

  @Test
  void anUnreadableWindowEmptiesTheList() {
    for (Object bad :
        List.of(
            Map.of("day", 8, "start", "09:00", "end", "10:00"),
            Map.of("day", 1, "start", "9:00", "end", "10:00"),
            Map.of("day", 1, "start", "10:00", "end", "09:00"),
            Map.of("day", "1", "start", "09:00", "end", "10:00"),
            Map.of("start", "09:00", "end", "10:00"),
            "lundi")) {
      Map<String, Object> p = complete();
      p.put("booking.windows", List.of(Map.of("day", 2, "start", "09:00", "end", "10:00"), bad));
      assertThat(BookingSettingsReader.of(p).windows()).as(String.valueOf(bad)).isEmpty();
    }
    Map<String, Object> p = complete();
    p.put("booking.windows", "lundi matin");
    assertThat(BookingSettingsReader.of(p).windows()).isEmpty();
  }

  @Test
  void emptyModalitiesFallBackToInPerson() {
    Map<String, Object> p = complete();
    p.put("booking.modalities", List.of());
    assertThat(BookingSettingsReader.of(p).modalities())
        .containsExactly(AppointmentModality.in_person);
  }
}
