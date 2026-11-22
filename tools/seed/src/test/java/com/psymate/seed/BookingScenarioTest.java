package com.psymate.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.psymate.seed.BookingScenario.Guest;
import com.psymate.seed.BookingScenario.GuestState;
import com.psymate.seed.BookingScenario.OnlineSeen;
import com.psymate.seed.Plan.Practitioner;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * La prise de rendez-vous de démonstration (M14-08) se décide sans réseau : réglages de K (Claire
 * en manual, Karim en auto), données fictives, choix déterministe des trois créneaux dans les sept
 * jours à venir.
 */
class BookingScenarioTest {

  private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z"); // dimanche, 16 h Paris

  @Test
  void claireIsManualOnWeekdayMorningsAndAfternoons() {
    Map<String, Object> p = BookingScenario.preferences(Practitioner.CLAIRE);
    assertEquals(true, p.get("booking.enabled"));
    assertEquals("manual", p.get("booking.confirmation"));
    assertEquals(60, p.get("booking.slotDurationMin"));
    assertEquals(24, p.get("booking.minNoticeHours"));
    assertEquals(28, p.get("booking.horizonDays"));
    assertEquals(List.of("in_person", "video"), p.get("booking.modalities"));
    assertEquals("Europe/Paris", p.get("booking.timezone"));
    List<?> windows = (List<?>) p.get("booking.windows");
    assertEquals(10, windows.size());
    for (int day = 1; day <= 5; day++) {
      assertTrue(windows.contains(Map.of("day", day, "start", "09:00", "end", "12:00")));
      assertTrue(windows.contains(Map.of("day", day, "start", "14:00", "end", "18:00")));
    }
    assertEquals(8, p.size(), "les huit clés booking.* de Q75");
  }

  @Test
  void karimIsAutoWithAFewWindows() {
    Map<String, Object> p = BookingScenario.preferences(Practitioner.KARIM);
    assertEquals(true, p.get("booking.enabled"));
    assertEquals("auto", p.get("booking.confirmation"));
    List<?> windows = (List<?>) p.get("booking.windows");
    assertTrue(!windows.isEmpty() && windows.size() <= 3);
    assertEquals(45, p.get("booking.slotDurationMin"));
    assertEquals(List.of("video", "phone"), p.get("booking.modalities"));
    assertEquals("Europe/Paris", p.get("booking.timezone"));
    assertEquals(8, p.size());
  }

  @Test
  void profilesCanBePublishedWithFictionalContacts() {
    for (Practitioner who : Practitioner.values()) {
      Map<String, Object> p = BookingScenario.profile(who);
      assertTrue(p.get("displayName") instanceof String s && !s.isBlank());
      assertTrue(p.get("city") instanceof String s && !s.isBlank());
      // contact affiché et renseigné : exigé par la publication
      assertEquals(true, p.get("showEmail"));
      assertTrue(String.valueOf(p.get("contactEmail")).endsWith("@example.test"));
      // plage de numéros réservée à la fiction (ARCEP : 06 39 98 xx xx)
      assertTrue(String.valueOf(p.get("contactPhone")).startsWith("+33 6 39 98 "));
    }
  }

  @Test
  void guestsAreFictionalAndDistinct() {
    List<Guest> all = new ArrayList<>(BookingScenario.PENDING);
    all.add(BookingScenario.CONFIRMED);
    assertEquals(3, all.size());
    Set<String> emails = new HashSet<>();
    for (Guest g : all) {
      assertTrue(g.email().endsWith("@example.test"), g.email());
      assertTrue(emails.add(g.email()), "une adresse par demande (G Q79)");
      assertTrue(g.phone() == null || g.phone().startsWith("+33 6 39 98 "), g.phone());
      assertTrue(List.of("in_person", "video").contains(g.modality()));
      assertTrue(!g.firstName().isBlank() && !g.lastName().isBlank());
    }
  }

  @Test
  void choosesThreeDistinctSlotsOnThreeDaysWithinSevenDays() {
    List<Instant> offered = weekdaySlots(LocalDate.parse("2026-11-23"), 28);
    List<Instant> chosen = BookingScenario.choose(offered, NOW);
    assertEquals(3, chosen.size());
    assertEquals(3, new HashSet<>(chosen).size());
    Set<LocalDate> days = new HashSet<>();
    for (Instant s : chosen) {
      assertTrue(offered.contains(s));
      assertTrue(s.isAfter(NOW) && s.isBefore(NOW.plus(Duration.ofDays(7))), s.toString());
      days.add(s.atZone(BookingScenario.ZONE).toLocalDate());
    }
    assertEquals(3, days.size());
    // le rendez-vous confirmé tombe le premier jour, avant les deux demandes en attente
    assertTrue(chosen.get(2).isBefore(chosen.get(0)));
    assertTrue(chosen.get(0).isBefore(chosen.get(1)));
    // déterministe : même liste, même résultat, dans n'importe quel ordre
    List<Instant> shuffled = new ArrayList<>(offered);
    java.util.Collections.reverse(shuffled);
    assertEquals(chosen, BookingScenario.choose(shuffled, NOW));
  }

  @Test
  void ignoresSlotsBeyondSevenDays() {
    List<Instant> offered = new ArrayList<>(weekdaySlots(LocalDate.parse("2026-11-30"), 7));
    offered.add(at("2026-11-24", "10:00"));
    // un seul jour dans la fenêtre, avec un seul créneau : pas assez
    assertThrows(SeedException.class, () -> BookingScenario.choose(offered, NOW));
  }

  @Test
  void fewerThanThreeDaysStillGivesDistinctSlots() {
    List<Instant> offered =
        List.of(
            at("2026-11-24", "09:00"),
            at("2026-11-24", "10:00"),
            at("2026-11-24", "11:00"),
            at("2026-11-25", "14:00"));
    List<Instant> chosen = BookingScenario.choose(offered, NOW);
    assertEquals(3, new HashSet<>(chosen).size());
    assertNotEquals(chosen.get(0), chosen.get(1));
  }

  @Test
  void approvalSqlOnlyTouchesSeedAccountsAwaitingVerification() {
    String sql = LocalApproval.sql(List.of("claire.seed@example.test", "karim.seed@example.test"));
    assertTrue(sql.startsWith("UPDATE practitioner SET verification_status = 'approved'"));
    assertTrue(sql.contains("role = 'practitioner'"));
    assertTrue(sql.contains("('docs_pending', 'under_review', 'needs_more')"));
    assertThrows(
        IllegalArgumentException.class, () -> LocalApproval.sql(List.of("x@example.test")));
    assertThrows(
        IllegalArgumentException.class,
        () -> LocalApproval.sql(List.of("a.seed@example.test' OR '1'='1")));
  }

  @Test
  void slotsAtOrBeforeNowAreIgnored() {
    List<Instant> offered =
        List.of(
            NOW.minus(Duration.ofHours(2)),
            NOW,
            at("2026-11-24", "09:00"),
            at("2026-11-25", "09:00"),
            at("2026-11-26", "09:00"));
    List<Instant> chosen = BookingScenario.choose(offered, NOW);
    assertEquals(3, chosen.size());
    for (Instant s : chosen) {
      assertTrue(s.isAfter(NOW), s.toString());
    }
    // seuls deux créneaux futurs : pas assez, les créneaux passés ne comptent pas
    assertThrows(
        SeedException.class,
        () ->
            BookingScenario.choose(
                List.of(
                    NOW.minusSeconds(60),
                    NOW,
                    at("2026-11-24", "09:00"),
                    at("2026-11-25", "09:00")),
                NOW));
  }

  // ---- idempotence par demandeur

  private static final String INES = BookingScenario.PENDING.get(0).email();
  private static final String HUGO = BookingScenario.PENDING.get(1).email();
  private static final String NADIA = BookingScenario.CONFIRMED.email();

  private static OnlineSeen seen(String email, String status) {
    return new OnlineSeen(email, status, null);
  }

  @Test
  void emptyAgendaWritesEveryGuestInOrder() {
    assertEquals(BookingScenario.guests(), BookingScenario.toWrite(List.of()));
    assertEquals(3, BookingScenario.problems(List.of()).size());
    assertFalse(BookingScenario.needsConfirmation(List.of()));
  }

  @Test
  void interruptedRunWritesOnlyMissingGuestsAndConfirmsNadia() {
    // premier passage interrompu par un 429 : Inès écrite, Hugo et Nadia absents
    List<OnlineSeen> afterInes = List.of(seen(INES, "tentative"));
    assertEquals(
        List.of(BookingScenario.PENDING.get(1), BookingScenario.CONFIRMED),
        BookingScenario.toWrite(afterInes));

    // premier passage interrompu avant la confirmation : rien à écrire, Nadia à confirmer
    List<OnlineSeen> unconfirmed =
        List.of(seen(INES, "tentative"), seen(HUGO, "tentative"), seen(NADIA, "tentative"));
    assertTrue(BookingScenario.toWrite(unconfirmed).isEmpty());
    assertTrue(BookingScenario.needsConfirmation(unconfirmed));
    assertEquals(1, BookingScenario.problems(unconfirmed).size(), "Nadia pas encore confirmée");

    // tout en place : rien à faire, aucun écart
    List<OnlineSeen> done =
        List.of(seen(INES, "tentative"), seen(HUGO, "tentative"), seen(NADIA, "planned"));
    assertTrue(BookingScenario.toWrite(done).isEmpty());
    assertFalse(BookingScenario.needsConfirmation(done));
    assertEquals(List.of(), BookingScenario.problems(done));
  }

  @Test
  void addressIsComparedWithoutCaseOrBlanks() {
    List<OnlineSeen> seen =
        List.of(
            seen(" " + INES.toUpperCase(java.util.Locale.ROOT) + " ", "tentative"),
            seen(null, "cancelled"), // invité purgé : ne compte pour personne
            seen("autre@example.test", "tentative"));
    assertEquals(GuestState.PENDING, BookingScenario.state(BookingScenario.PENDING.get(0), seen));
    assertEquals(2, BookingScenario.toWrite(seen).size());
  }

  @Test
  void onlyAnExpiryIsTolerated() {
    List<OnlineSeen> expired =
        List.of(
            new OnlineSeen(INES, "cancelled", "Demande expirée"),
            new OnlineSeen(HUGO, "cancelled", "Request expired"),
            seen(NADIA, "planned"));
    assertTrue(
        BookingScenario.toWrite(expired).isEmpty(), "une demande expirée n'est pas réécrite");
    assertEquals(
        GuestState.EXPIRED, BookingScenario.state(BookingScenario.PENDING.get(0), expired));
    assertEquals(List.of(), BookingScenario.problems(expired));

    List<OnlineSeen> refused =
        List.of(
            new OnlineSeen(INES, "cancelled", "Créneau indisponible"),
            seen(HUGO, "tentative"),
            seen(NADIA, "planned"));
    assertTrue(BookingScenario.toWrite(refused).isEmpty(), "présent, même annulé");
    assertEquals(
        GuestState.CANCELLED, BookingScenario.state(BookingScenario.PENDING.get(0), refused));
    assertEquals(1, BookingScenario.problems(refused).size());

    // une nouvelle demande active l'emporte sur une ancienne annulation
    List<OnlineSeen> again =
        List.of(new OnlineSeen(INES, "cancelled", "Créneau indisponible"), seen(INES, "tentative"));
    assertEquals(GuestState.PENDING, BookingScenario.state(BookingScenario.PENDING.get(0), again));
  }

  @Test
  void pgContainerNameIsValidated() {
    assertTrue(LocalApproval.validContainer("psymate-api-postgres-1"));
    assertTrue(LocalApproval.validContainer("m1408_pg.2"));
    assertTrue(LocalApproval.validContainer("2b74bf6aed7d"));
    assertFalse(LocalApproval.validContainer("-it"));
    assertFalse(LocalApproval.validContainer("--privileged"));
    assertFalse(LocalApproval.validContainer("pg; rm -rf /"));
    assertFalse(LocalApproval.validContainer(""));
    assertThrows(IllegalArgumentException.class, () -> new LocalApproval("-e"));
  }

  // ---- outils

  private static Instant at(String day, String time) {
    return ZonedDateTime.of(LocalDate.parse(day), LocalTime.parse(time), BookingScenario.ZONE)
        .toInstant();
  }

  /** Créneaux de 60 min de Claire (lun.–ven., 9–12 h et 14–18 h) sur {@code days} jours. */
  private static List<Instant> weekdaySlots(LocalDate first, int days) {
    List<Instant> slots = new ArrayList<>();
    for (LocalDate d = first; d.isBefore(first.plusDays(days)); d = d.plusDays(1)) {
      if (d.getDayOfWeek().getValue() > 5) {
        continue;
      }
      for (int h : new int[] {9, 10, 11, 14, 15, 16, 17}) {
        slots.add(ZonedDateTime.of(d, LocalTime.of(h, 0), BookingScenario.ZONE).toInstant());
      }
    }
    return slots;
  }
}
