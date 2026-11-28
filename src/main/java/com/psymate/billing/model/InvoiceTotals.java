package com.psymate.billing.model;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Calculs d'une facture (L3.2, G Q84, Q88, Q92), en centimes d'euro entiers ({@code long}) : aucun
 * nombre décimal, aucun {@code double}.
 *
 * <ul>
 *   <li>{@code line.amount = quantity × unitPrice} ; une remise est une ligne à prix négatif ;
 *   <li>{@code subtotal = somme des lignes} ;
 *   <li>{@code vat = régime liable ? arrondi(subtotal × taux / 10000) : 0}, taux en points de base
 *       (2000 = 20 %) ; {@code total = subtotal + vat} ;
 *   <li>{@code balanceDue = max(0, total − paid − credited)}.
 * </ul>
 *
 * <p><b>Arrondi de la TVA</b> : au centime le plus proche, une demi-unité arrondie vers le haut
 * ({@code floor(x + 0,5)}, arrondi « demi supérieur ») : 1 234 × 5,5 % = 67,87 → 68 ; 1 010 × 5 % =
 * 50,5 → 51. Le calcul est exact (entiers), sans passer par un nombre flottant ; la requête de
 * liste ({@code InvoiceNativeQueries}) calcule le total d'un brouillon par la même formule en SQL
 * ({@code floor(subtotal × taux / 10000,0 + 0,5)}, en {@code numeric} exact), si bien que la liste
 * et le détail donnent toujours le même total. Pour une facture émise (sous-total positif), c'est
 * l'arrondi commercial habituel.
 *
 * @param subtotal somme des lignes, en centimes (négative possible sur un brouillon)
 * @param vat TVA en centimes, 0 hors régime assujetti
 * @param total sous-total plus TVA, en centimes
 */
public record InvoiceTotals(long subtotal, long vat, long total) {

  /** Régime assujetti à la TVA (G Q92). */
  public static final String LIABLE = "liable";

  /** Taux de TVA en points de base pour 100 % (10000 = 100 %). */
  static final long BASIS_POINTS = 10_000L;

  /** Montant d'une ligne : quantité × prix unitaire, en centimes. */
  public static long lineAmount(int quantity, int unitPriceCents) {
    return (long) quantity * unitPriceCents;
  }

  /**
   * TVA d'un sous-total : arrondi demi supérieur de {@code subtotal × rateBp / 10000} en régime
   * {@code liable} avec un taux ; 0 sinon (régimes {@code health_exempt} et {@code franchise}).
   */
  public static long vat(long subtotal, String regime, Integer rateBp) {
    if (!LIABLE.equals(regime) || rateBp == null) {
      return 0L;
    }
    return roundHalfUp(Math.multiplyExact(subtotal, (long) rateBp), BASIS_POINTS);
  }

  /** {@code floor(numerator / denominator + 1/2)}, exact, pour un dénominateur positif. */
  static long roundHalfUp(long numerator, long denominator) {
    return Math.floorDiv(
        Math.addExact(Math.multiplyExact(numerator, 2L), denominator), 2L * denominator);
  }

  /** Totaux d'une liste de montants de lignes, selon le régime et le taux. */
  public static InvoiceTotals of(Collection<Long> lineAmounts, String regime, Integer rateBp) {
    long subtotal = 0L;
    for (long amount : lineAmounts) {
      subtotal = Math.addExact(subtotal, amount);
    }
    return ofSubtotal(subtotal, regime, rateBp);
  }

  /** Totaux d'un sous-total, selon le régime et le taux. */
  public static InvoiceTotals ofSubtotal(long subtotal, String regime, Integer rateBp) {
    long vat = vat(subtotal, regime, rateBp);
    return new InvoiceTotals(subtotal, vat, Math.addExact(subtotal, vat));
  }

  /** Reste dû : total moins payé moins crédité, jamais négatif (L3.2). */
  public static long balanceDue(long total, long paid, long credited) {
    return Math.max(0L, total - paid - credited);
  }

  /**
   * Statut exposé (G Q88, L3.2) : {@code draft} pour un brouillon ; {@code issued} pour un avoir ;
   * {@code void} pour une facture annulée ; sinon {@code paid} si le reste dû est nul, {@code
   * partial} si quelque chose a été payé ou crédité, {@code due} sinon. Même règle que la requête
   * de liste, en SQL.
   */
  public static String status(
      String kind, String storedStatus, long total, long paid, long credited) {
    if (Invoice.DRAFT.equals(storedStatus)) {
      return Invoice.DRAFT;
    }
    if (Invoice.KIND_CREDIT_NOTE.equals(kind)) {
      return Invoice.ISSUED;
    }
    if (Invoice.VOID.equals(storedStatus)) {
      return Invoice.VOID;
    }
    if (balanceDue(total, paid, credited) == 0L) {
      return "paid";
    }
    return paid + credited > 0L ? "partial" : "due";
  }

  /**
   * Échéance effective (L3.2) : {@code on_receipt} → date d'émission ; {@code days_15}, {@code
   * days_30} → date d'émission plus 15 ou 30 jours ; {@code date} → la date choisie.
   */
  public static LocalDate dueOn(String dueTerm, LocalDate issueDate, LocalDate chosen) {
    if (chosen != null) {
      return chosen;
    }
    return switch (dueTerm) {
      case "days_15" -> issueDate.plusDays(15);
      case "days_30" -> issueDate.plusDays(30);
      default -> issueDate;
    };
  }

  /**
   * En retard (G Q88) : facture émise (ni brouillon, ni avoir, ni annulée), échéance strictement
   * avant aujourd'hui (au jour de Europe/Paris) et reste dû positif.
   */
  public static boolean overdue(
      String kind, String storedStatus, LocalDate dueOn, long balanceDue, LocalDate today) {
    return Invoice.KIND_INVOICE.equals(kind)
        && Invoice.ISSUED.equals(storedStatus)
        && balanceDue > 0L
        && dueOn.isBefore(today);
  }
}
