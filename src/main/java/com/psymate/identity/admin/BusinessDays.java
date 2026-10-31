package com.psymate.identity.admin;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Calcul en jours ouvrés pour le délai de traitement des dossiers (SLA de 2 jours ouvrés, fiche
 * 20).
 *
 * <p>Convention : les jours ouvrés sont du lundi au vendredi, en UTC, sans jours fériés. L'heure de
 * la journée est conservée : vendredi 17:00 + 2 jours ouvrés = mardi 17:00.
 */
final class BusinessDays {

  private BusinessDays() {}

  /**
   * Instant obtenu en ajoutant {@code days} jours ouvrés (lundi à vendredi, UTC) à {@code from}.
   */
  static Instant addBusinessDays(Instant from, int days) {
    LocalDateTime t = LocalDateTime.ofInstant(from, ZoneOffset.UTC);
    int remaining = days;
    while (remaining > 0) {
      t = t.plusDays(1);
      DayOfWeek d = t.getDayOfWeek();
      if (d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY) {
        remaining--;
      }
    }
    return t.toInstant(ZoneOffset.UTC);
  }
}
