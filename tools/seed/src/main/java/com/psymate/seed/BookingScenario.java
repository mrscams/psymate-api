package com.psymate.seed;

import com.psymate.seed.Plan.Practitioner;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Prise de rendez-vous en ligne de démonstration (M14-08, document K) : fiche publique, réglages
 * {@code booking.*} de chaque praticien du seed, demandeurs fictifs et choix des créneaux. Pur et
 * déterministe, sans HTTP ; {@link BookingSeeder} l'écrit par l'API.
 *
 * <ul>
 *   <li>Claire (praticienne de démonstration) : {@code manual}, du lundi au vendredi 09:00–12:00 et
 *       14:00–18:00, créneaux de 60 min, délai 24 h, horizon 28 jours, cabinet et visio.
 *   <li>Karim (second praticien publié) : {@code auto}, deux plages (mardi après-midi, jeudi
 *       matin), créneaux de 45 min, visio et téléphone.
 * </ul>
 */
public final class BookingScenario {

  /** Fuseau des plages des deux praticiens. */
  public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

  /** Les demandes de démonstration tombent dans les sept jours à venir. */
  public static final Duration REQUEST_WINDOW = Duration.ofDays(7);

  /** Un demandeur fictif : adresse en example.test, téléphone de la plage réservée à la fiction. */
  public record Guest(
      String firstName, String lastName, String email, String phone, String modality) {}

  /** Les deux demandes en attente de Claire (mode manual), dans cet ordre. */
  public static final List<Guest> PENDING =
      List.of(
          new Guest("Inès", "Fabre", "ines.fabre.rdv@example.test", "+33 6 39 98 00 11", "video"),
          new Guest("Hugo", "Lenoir", "hugo.lenoir.rdv@example.test", null, "in_person"));

  /** La demande que Claire confirme : un rendez-vous en ligne confirmé. */
  public static final Guest CONFIRMED =
      new Guest(
          "Nadia", "Perrin", "nadia.perrin.rdv@example.test", "+33 6 39 98 00 12", "in_person");

  private BookingScenario() {}

  // ---- fiche publique (PUT /api/me/profile)

  /** Contenu de la fiche d'annuaire publiée (assez pour POST /api/me/profile/publish). */
  public static Map<String, Object> profile(Practitioner who) {
    Map<String, Object> p = new LinkedHashMap<>();
    switch (who) {
      case CLAIRE -> {
        p.put("displayName", "Claire Martin");
        p.put("titleLabel", "Psychologue clinicienne");
        p.put(
            "bio",
            "Données fictives de démonstration. Accompagnement des adultes et des jeunes adultes :"
                + " anxiété, épuisement professionnel, transitions de vie.");
        p.put("specialties", List.of("anxiety", "burnout"));
        p.put("audiences", List.of("adults", "young_adults"));
        p.put("modalities", List.of("in_person", "video"));
        p.put("languages", List.of("fr", "en"));
        p.put("city", "Lyon");
        p.put("postalCode", "69003");
        p.put("contactPhone", "+33 6 39 98 00 01");
        p.put("contactEmail", "cabinet.claire.seed@example.test");
      }
      case KARIM -> {
        p.put("displayName", "Karim Haddad");
        p.put("titleLabel", "Psychiatre");
        p.put(
            "bio",
            "Données fictives de démonstration. Consultations de psychiatrie adulte, suivi des"
                + " traitements.");
        p.put("specialties", List.of("depression", "adhd"));
        p.put("audiences", List.of("adults"));
        p.put("modalities", List.of("video"));
        p.put("languages", List.of("fr"));
        p.put("city", "Villeurbanne");
        p.put("postalCode", "69100");
        p.put("contactPhone", "+33 6 39 98 00 02");
        p.put("contactEmail", "cabinet.karim.seed@example.test");
      }
    }
    p.put("country", "FR");
    p.put("showPhone", false);
    p.put("showEmail", true);
    p.put("showAddress", false);
    p.put("availability", "accepting");
    return p;
  }

  // ---- réglages (PATCH /api/me/preferences)

  /** Les huit clés {@code booking.*} (Q75) du praticien. */
  public static Map<String, Object> preferences(Practitioner who) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("booking.enabled", true);
    switch (who) {
      case CLAIRE -> {
        List<Map<String, Object>> windows = new ArrayList<>();
        for (int day = 1; day <= 5; day++) {
          windows.add(window(day, "09:00", "12:00"));
          windows.add(window(day, "14:00", "18:00"));
        }
        p.put("booking.confirmation", "manual");
        p.put("booking.windows", windows);
        p.put("booking.slotDurationMin", 60);
        p.put("booking.minNoticeHours", 24);
        p.put("booking.horizonDays", 28);
        p.put("booking.modalities", List.of("in_person", "video"));
      }
      case KARIM -> {
        p.put("booking.confirmation", "auto");
        p.put("booking.windows", List.of(window(2, "14:00", "17:00"), window(4, "09:00", "12:00")));
        p.put("booking.slotDurationMin", 45);
        p.put("booking.minNoticeHours", 24);
        p.put("booking.horizonDays", 28);
        p.put("booking.modalities", List.of("video", "phone"));
      }
    }
    p.put("booking.timezone", ZONE.getId());
    return p;
  }

  private static Map<String, Object> window(int day, String start, String end) {
    Map<String, Object> w = new LinkedHashMap<>();
    w.put("day", day);
    w.put("start", start);
    w.put("end", end);
    return w;
  }

  // ---- choix des créneaux

  /**
   * Créneaux des trois demandes de Claire, dans l'ordre {@code PENDING[0]}, {@code PENDING[1]},
   * {@code CONFIRMED}, tous parmi les créneaux offerts qui commencent avant {@code now + 7 jours}.
   * Sur trois jours distincts quand il y en a trois : le rendez-vous confirmé le premier jour, les
   * deux demandes en attente les deux jours suivants ; chaque fois le créneau du milieu du jour.
   * Avec moins de trois jours, des créneaux distincts pris à tour de rôle dans les jours. Même
   * liste, même résultat.
   *
   * @throws SeedException s'il y a moins de trois créneaux offerts dans la fenêtre
   */
  public static List<Instant> choose(List<Instant> offered, Instant now) {
    Instant limit = now.plus(REQUEST_WINDOW);
    Map<LocalDate, List<Instant>> byDay = new TreeMap<>();
    offered.stream()
        .filter(s -> s.isAfter(now) && s.isBefore(limit))
        .sorted()
        .distinct()
        .forEach(
            s ->
                byDay.computeIfAbsent(s.atZone(ZONE).toLocalDate(), d -> new ArrayList<>()).add(s));
    List<List<Instant>> days = new ArrayList<>(byDay.values());
    int total = days.stream().mapToInt(List::size).sum();
    if (total < 3) {
      throw new SeedException(
          "Prise de rendez-vous : "
              + total
              + " créneau(x) offert(s) dans les sept jours à venir, 3 attendus.");
    }
    List<Instant> picked = new ArrayList<>();
    if (days.size() >= 3) {
      for (int d = 0; d < 3; d++) {
        List<Instant> day = days.get(d);
        picked.add(day.get(day.size() / 2));
      }
    } else {
      // à tour de rôle : jour 0, jour 1, jour 0, … en avançant dans chaque jour
      int[] next = new int[days.size()];
      for (int d = 0; picked.size() < 3; d = (d + 1) % days.size()) {
        if (next[d] < days.get(d).size()) {
          picked.add(days.get(d).get(next[d]++));
        }
      }
    }
    // picked = [jour 0, jour 1, jour 2] -> [attente, attente, confirmé] = [1, 2, 0]
    return List.of(picked.get(1), picked.get(2), picked.get(0));
  }
}
