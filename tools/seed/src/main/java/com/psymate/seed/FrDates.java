package com.psymate.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Libellés de période en français (« été 2019 », « de mars à juin 2022 »). */
final class FrDates {

  private FrDates() {}

  private static final String[] MONTHS = {
    "janvier",
    "février",
    "mars",
    "avril",
    "mai",
    "juin",
    "juillet",
    "août",
    "septembre",
    "octobre",
    "novembre",
    "décembre"
  };

  static String monthName(int month) {
    return MONTHS[month - 1];
  }

  /** Saison météorologique : printemps mars-mai, été juin-août, automne sept.-nov., hiver sinon. */
  static String season(LocalDate d) {
    return switch (d.getMonthValue()) {
      case 3, 4, 5 -> "printemps";
      case 6, 7, 8 -> "été";
      case 9, 10, 11 -> "automne";
      default -> "hiver";
    };
  }

  static String seasonYear(LocalDate d) {
    // décembre appartient à l'hiver qui finit l'année suivante
    int year = d.getMonthValue() == 12 ? d.getYear() + 1 : d.getYear();
    return season(d) + " " + year;
  }

  static String periodLabel(Instant from, Instant to) {
    LocalDate a = from.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate b = to.atZone(ZoneOffset.UTC).toLocalDate();
    if (seasonYear(a).equals(seasonYear(b))) {
      return seasonYear(a);
    }
    if (a.getYear() == b.getYear()) {
      return "de "
          + monthName(a.getMonthValue())
          + " à "
          + monthName(b.getMonthValue())
          + " "
          + a.getYear();
    }
    return "de "
        + monthName(a.getMonthValue())
        + " "
        + a.getYear()
        + " à "
        + monthName(b.getMonthValue())
        + " "
        + b.getYear();
  }

  /** « depuis l'été 2023 » : libellé d'une période encore ouverte. */
  static String sinceLabel(Instant from) {
    LocalDate a = from.atZone(ZoneOffset.UTC).toLocalDate();
    String s = seasonYear(a);
    String article =
        s.startsWith("été") || s.startsWith("automne") || s.startsWith("hiver") ? "l'" : "le ";
    return "depuis " + article + s;
  }
}
