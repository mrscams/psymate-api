package com.psymate.billing.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Calculs d'une facture (L3.2, G Q84, Q88, Q92), en centimes entiers. */
class InvoiceTotalsTest {

  private static final LocalDate ISSUED_ON = LocalDate.of(2026, 11, 5);

  @Test
  void aLineAmountIsQuantityTimesUnitPrice() {
    assertThat(InvoiceTotals.lineAmount(1, 6000)).isEqualTo(6000L);
    assertThat(InvoiceTotals.lineAmount(3, 5500)).isEqualTo(16_500L);
    // Sans débordement : 999 × 10 000 000 centimes tient dans un long.
    assertThat(InvoiceTotals.lineAmount(999, 10_000_000)).isEqualTo(9_990_000_000L);
  }

  @Test
  void aDiscountIsANegativeLineSubtractedFromTheSubtotal() {
    long lines = InvoiceTotals.lineAmount(3, 6000);
    long discount = InvoiceTotals.lineAmount(1, -1500);
    InvoiceTotals totals = InvoiceTotals.of(List.of(lines, discount), "health_exempt", null);
    assertThat(totals).isEqualTo(new InvoiceTotals(16_500L, 0L, 16_500L));
    assertThat(discount).isEqualTo(-1500L);
  }

  @Test
  void anEmptyInvoiceTotalsZero() {
    assertThat(InvoiceTotals.of(List.of(), "liable", 2000)).isEqualTo(new InvoiceTotals(0, 0, 0));
  }

  @Test
  void healthExemptRegimeHasNoVat() {
    assertThat(InvoiceTotals.ofSubtotal(18_000L, "health_exempt", null))
        .isEqualTo(new InvoiceTotals(18_000L, 0L, 18_000L));
    // Un taux resté en mémoire ne compte pas hors régime assujetti.
    assertThat(InvoiceTotals.vat(18_000L, "health_exempt", 2000)).isZero();
  }

  @Test
  void franchiseRegimeHasNoVat() {
    assertThat(InvoiceTotals.ofSubtotal(18_000L, "franchise", null))
        .isEqualTo(new InvoiceTotals(18_000L, 0L, 18_000L));
    assertThat(InvoiceTotals.vat(18_000L, "franchise", 2000)).isZero();
  }

  @Test
  void liableRegimeAddsVatAtTheRateInBasisPoints() {
    assertThat(InvoiceTotals.ofSubtotal(10_000L, "liable", 2000))
        .isEqualTo(new InvoiceTotals(10_000L, 2000L, 12_000L));
    assertThat(InvoiceTotals.ofSubtotal(6000L, "liable", 0))
        .isEqualTo(new InvoiceTotals(6000L, 0L, 6000L));
    // Régime assujetti sans taux (impossible en base) : pas de TVA plutôt qu'une erreur.
    assertThat(InvoiceTotals.vat(6000L, "liable", null)).isZero();
  }

  @Test
  void vatIsRoundedToTheNearestCentHalfUp() {
    // 1 234 × 5,5 % = 67,87 → 68 ; 1 010 × 5 % = 50,5 → 51 ; 1 001 × 5 % = 50,05 → 50.
    assertThat(InvoiceTotals.vat(1234L, "liable", 550)).isEqualTo(68L);
    assertThat(InvoiceTotals.vat(1010L, "liable", 500)).isEqualTo(51L);
    assertThat(InvoiceTotals.vat(1001L, "liable", 500)).isEqualTo(50L);
    // 333 × 20 % = 66,6 → 67 ; 6 × 10 % = 0,6 → 1 ; 4 × 10 % = 0,4 → 0.
    assertThat(InvoiceTotals.vat(333L, "liable", 2000)).isEqualTo(67L);
    assertThat(InvoiceTotals.vat(6L, "liable", 1000)).isEqualTo(1L);
    assertThat(InvoiceTotals.vat(4L, "liable", 1000)).isZero();
    // Demi supérieur, y compris sous zéro (brouillon dont la remise dépasse les lignes) :
    // -1 010 × 5 % = -50,5 → -50, comme floor(x + 0,5) en SQL.
    assertThat(InvoiceTotals.vat(-1010L, "liable", 500)).isEqualTo(-50L);
    assertThat(InvoiceTotals.vat(-1234L, "liable", 550)).isEqualTo(-68L);
  }

  @Test
  void theTotalIsSubtotalPlusRoundedVat() {
    InvoiceTotals totals =
        InvoiceTotals.of(
            List.of(InvoiceTotals.lineAmount(2, 6170), InvoiceTotals.lineAmount(1, -500)),
            "liable",
            2000);
    // 12 340 − 500 = 11 840 ; TVA 2 368 ; total 14 208.
    assertThat(totals).isEqualTo(new InvoiceTotals(11_840L, 2368L, 14_208L));
  }

  @Test
  void theBalanceDueIsNeverNegative() {
    assertThat(InvoiceTotals.balanceDue(18_000L, 6000L, 0L)).isEqualTo(12_000L);
    assertThat(InvoiceTotals.balanceDue(18_000L, 6000L, 12_000L)).isZero();
    assertThat(InvoiceTotals.balanceDue(18_000L, 18_000L, 6000L)).isZero();
  }

  @Test
  void theExposedStatusFollowsTheCycle() {
    assertThat(InvoiceTotals.status("invoice", "draft", 6000, 0, 0)).isEqualTo("draft");
    assertThat(InvoiceTotals.status("invoice", "issued", 6000, 0, 0)).isEqualTo("due");
    assertThat(InvoiceTotals.status("invoice", "issued", 6000, 2000, 0)).isEqualTo("partial");
    assertThat(InvoiceTotals.status("invoice", "issued", 6000, 0, 1000)).isEqualTo("partial");
    assertThat(InvoiceTotals.status("invoice", "issued", 6000, 6000, 0)).isEqualTo("paid");
    assertThat(InvoiceTotals.status("invoice", "issued", 6000, 1000, 5000)).isEqualTo("paid");
    assertThat(InvoiceTotals.status("invoice", "void", 6000, 0, 6000)).isEqualTo("void");
    assertThat(InvoiceTotals.status("credit_note", "issued", 6000, 0, 0)).isEqualTo("issued");
  }

  @Test
  void theDueDateFollowsTheTerm() {
    assertThat(InvoiceTotals.dueOn("on_receipt", ISSUED_ON, null)).isEqualTo(ISSUED_ON);
    assertThat(InvoiceTotals.dueOn("days_15", ISSUED_ON, null))
        .isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(InvoiceTotals.dueOn("days_30", ISSUED_ON, null))
        .isEqualTo(LocalDate.of(2026, 12, 5));
    assertThat(InvoiceTotals.dueOn("date", ISSUED_ON, LocalDate.of(2026, 12, 24)))
        .isEqualTo(LocalDate.of(2026, 12, 24));
  }

  @Test
  void overdueMeansIssuedPastDueWithABalance() {
    LocalDate today = LocalDate.of(2026, 11, 28);
    LocalDate past = LocalDate.of(2026, 11, 27);
    assertThat(InvoiceTotals.overdue("invoice", "issued", past, 6000, today)).isTrue();
    assertThat(InvoiceTotals.overdue("invoice", "issued", today, 6000, today)).isFalse();
    assertThat(InvoiceTotals.overdue("invoice", "issued", past, 0, today)).isFalse();
    assertThat(InvoiceTotals.overdue("invoice", "draft", past, 6000, today)).isFalse();
    assertThat(InvoiceTotals.overdue("invoice", "void", past, 6000, today)).isFalse();
    assertThat(InvoiceTotals.overdue("credit_note", "issued", past, 6000, today)).isFalse();
  }
}
