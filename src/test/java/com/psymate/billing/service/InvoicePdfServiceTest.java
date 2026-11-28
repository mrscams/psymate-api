package com.psymate.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.billing.model.BillingException;
import com.psymate.billing.model.BillingIssuer;
import com.psymate.billing.model.Invoice;
import com.psymate.billing.model.InvoiceLine;
import com.psymate.billing.model.InvoiceTotals;
import com.psymate.billing.model.Payment;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Feuille PDF d'une facture (M15-09) : textes fixes en français, mise en forme, échappement. */
class InvoicePdfServiceTest {

  private static final String NBSP = " ";
  private static final OffsetDateTime NOW =
      OffsetDateTime.of(2026, 11, 28, 9, 0, 0, 0, ZoneOffset.UTC);
  private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

  private final InvoicePdfService service =
      new InvoicePdfService(null, null, null, null, null, null, new BillingTexts());

  @Test
  void amountsRatesSiretAndIbanAreFormattedTheFrenchWay() {
    assertThat(InvoicePdfService.euros(6000)).isEqualTo("60,00" + NBSP + "€");
    assertThat(InvoicePdfService.euros(123_456_789))
        .isEqualTo("1" + NBSP + "234" + NBSP + "567,89" + NBSP + "€");
    assertThat(InvoicePdfService.euros(-505)).isEqualTo("-5,05" + NBSP + "€");
    assertThat(InvoicePdfService.euros(0)).isEqualTo("0,00" + NBSP + "€");
    assertThat(InvoicePdfService.rate(2000)).isEqualTo("20" + NBSP + "%");
    assertThat(InvoicePdfService.rate(550)).isEqualTo("5,5" + NBSP + "%");
    assertThat(InvoicePdfService.rate(1025)).isEqualTo("10,25" + NBSP + "%");
    assertThat(InvoicePdfService.siret("73282932000074")).isEqualTo("732 829 320 00074");
    assertThat(InvoicePdfService.groupsOfFour("FR7630006000011234567890189"))
        .isEqualTo("FR76 3000 6000 0112 3456 7890 189");
  }

  @Test
  void theVariantIsAbsentOrReceipted() {
    assertThat(InvoicePdfService.receipted(null)).isFalse();
    assertThat(InvoicePdfService.receipted("")).isFalse();
    assertThat(InvoicePdfService.receipted("receipted")).isTrue();
    assertThatThrownBy(() -> InvoicePdfService.receipted("Receipted"))
        .isInstanceOf(BillingException.class)
        .satisfies(e -> assertThat(((BillingException) e).field()).isEqualTo("variant"));
  }

  @Test
  void aDraftSheetHasAWatermarkNoNumberAndTheModesOfTheSettings() {
    Invoice draft = draft();
    InvoiceSheet sheet =
        service.sheet(
            draft,
            List.of(line(draft, 6000, 2)),
            issuer("health_exempt", null, true),
            InvoiceTotals.ofSubtotal(12_000, "health_exempt", null),
            List.of(),
            InvoicePdfService.Variant.DRAFT,
            "FR7630006000011234567890189");
    assertThat(sheet.watermark()).isEqualTo("Brouillon");
    assertThat(sheet.stamp()).isNull();
    assertThat(sheet.title()).isEqualTo("Facture");
    assertThat(sheet.number()).isEqualTo("N° attribué à l’émission");
    assertThat(sheet.issuerLines())
        .containsExactly(
            "Psychologue",
            "12 rue des Lilas",
            "75011 Paris",
            "SIRET 732 829 320 00074",
            "N° professionnel 75 93 0000 0");
    assertThat(sheet.recipientLines()).containsExactly("8 avenue Parmentier", "75011 Paris");
    assertThat(sheet.meta())
        .extracting(InvoiceSheet.Meta::label, InvoiceSheet.Meta::value)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Date", "20 novembre 2026"),
            org.assertj.core.groups.Tuple.tuple("Échéance", "à réception"));
    assertThat(sheet.rows()).hasSize(1);
    assertThat(sheet.rows().getFirst().date()).isEqualTo("12/11/2026");
    assertThat(sheet.rows().getFirst().amount()).isEqualTo("120,00" + NBSP + "€");
    assertThat(sheet.totals())
        .extracting(InvoiceSheet.Total::label, InvoiceSheet.Total::big)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("Total", true));
    assertThat(sheet.legal())
        .containsExactly(
            "TVA non applicable, article 261-4-1° du Code général des impôts.",
            "Règlement par virement, chèque ou espèces, à l’ordre de Claire Moreau.",
            "IBAN FR76 3000 6000 0112 3456 7890 189",
            "Mention libre");
    assertThat(sheet.footer())
        .isEqualTo("Claire Moreau · Psychologue · claire@example.test · 01 00 00 00 00");
  }

  @Test
  void aLiableSheetShowsHtVatAndTtcAndTheVatNumber() {
    Invoice draft = draft();
    InvoiceSheet sheet =
        service.sheet(
            draft,
            List.of(line(draft, 10_000, 1)),
            issuer("liable", 2000, false),
            InvoiceTotals.ofSubtotal(10_000, "liable", 2000),
            List.of(),
            InvoicePdfService.Variant.ISSUED,
            null);
    assertThat(sheet.watermark()).isNull();
    assertThat(sheet.columns()).contains("Prix unitaire HT", "Montant HT");
    assertThat(sheet.issuerLines()).contains("N° de TVA intracommunautaire FR40303265045");
    assertThat(sheet.totals())
        .extracting(InvoiceSheet.Total::label, InvoiceSheet.Total::amount)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Total HT", "100,00" + NBSP + "€"),
            org.assertj.core.groups.Tuple.tuple("TVA 20" + NBSP + "%", "20,00" + NBSP + "€"),
            org.assertj.core.groups.Tuple.tuple("Total TTC", "120,00" + NBSP + "€"));
    // Pas de mention d'exonération en régime assujetti ; IBAN non montré.
    assertThat(sheet.legal().getFirst()).startsWith("Règlement par");
    assertThat(sheet.legal()).noneMatch(l -> l.startsWith("IBAN"));
  }

  @Test
  void aReceiptedSheetShowsTheStampAndThePaymentsButNotTheModes() {
    Invoice invoice = draft();
    invoice.issue(
        "F-2026-0146",
        issuer("health_exempt", null, false).toSnapshot(),
        DAY,
        InvoiceTotals.ofSubtotal(6000, "health_exempt", null),
        "billing/invoice/x.pdf",
        NOW);
    Payment payment =
        Payment.received(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            invoice.getId(),
            "transfer",
            6000,
            DAY,
            null,
            NOW);
    InvoiceSheet sheet =
        service.sheet(
            invoice,
            List.of(line(invoice, 6000, 1)),
            BillingIssuer.fromSnapshot(invoice.getIssuerSnapshot()),
            InvoiceTotals.ofSubtotal(6000, "health_exempt", null),
            List.of(payment),
            InvoicePdfService.Variant.RECEIPTED,
            null);
    assertThat(sheet.title()).isEqualTo("Facture acquittée");
    assertThat(sheet.stamp()).isEqualTo("Acquittée");
    assertThat(sheet.number()).isEqualTo("N° F-2026-0146");
    assertThat(sheet.meta())
        .extracting(InvoiceSheet.Meta::label)
        .containsExactly("Date", "Réglée le");
    assertThat(sheet.totals())
        .extracting(InvoiceSheet.Total::label)
        .containsExactly("Total", "Réglé par virement le 20/11/2026");
    assertThat(sheet.legal())
        .containsExactly(
            "TVA non applicable, article 261-4-1° du Code général des impôts.", "Mention libre");
  }

  @Test
  void anIssuedSheetWithAPaymentAtIssueShowsWhatIsLeftToPay() {
    Invoice invoice = draft();
    Payment payment =
        Payment.received(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            invoice.getId(),
            "cheque",
            6000,
            DAY,
            null,
            NOW);
    InvoiceSheet sheet =
        service.sheet(
            invoice,
            List.of(line(invoice, 6000, 1)),
            issuer("franchise", null, false),
            InvoiceTotals.ofSubtotal(6000, "franchise", null),
            List.of(payment),
            InvoicePdfService.Variant.ISSUED,
            null);
    assertThat(sheet.totals())
        .extracting(InvoiceSheet.Total::label, InvoiceSheet.Total::amount)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Total", "60,00" + NBSP + "€"),
            org.assertj.core.groups.Tuple.tuple(
                "Déjà réglé (chèque, 20/11/2026)", "-60,00" + NBSP + "€"),
            org.assertj.core.groups.Tuple.tuple("Reste à régler", "0,00" + NBSP + "€"));
    assertThat(sheet.legal().getFirst())
        .isEqualTo("TVA non applicable, article 293 B du Code général des impôts.");
  }

  @Test
  void theHtmlEscapesEveryTypedText() {
    Invoice draft =
        Invoice.draft(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "<script>alert('x')</script>",
            "1 rue \"A\" & B",
            DAY,
            "on_receipt",
            null,
            NOW);
    InvoiceLine line =
        new InvoiceLine(
            UUID.randomUUID(), draft.getId(), 1, null, "Atelier <b>&</b>", null, 1, 5000, null);
    String html =
        InvoicePdfRenderer.html(
            service.sheet(
                draft,
                List.of(line),
                issuer("health_exempt", null, false),
                InvoiceTotals.ofSubtotal(5000, "health_exempt", null),
                List.of(),
                InvoicePdfService.Variant.DRAFT,
                null));
    assertThat(html)
        .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;")
        .contains("1 rue &quot;A&quot; &amp; B")
        .contains("Atelier &lt;b&gt;&amp;&lt;/b&gt;")
        .doesNotContain("<script>")
        .doesNotContain("<b>&</b>")
        .contains("lang=\"fr\" dir=\"ltr\"");
    assertThat(InvoicePdfRenderer.esc("a\u0001b")).isEqualTo("ab");
  }

  private static Invoice draft() {
    return Invoice.draft(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Nour Khalil",
        "8 avenue Parmentier\n75011 Paris",
        DAY,
        "on_receipt",
        "Mention libre",
        NOW);
  }

  private static InvoiceLine line(Invoice invoice, int unitPrice, int quantity) {
    return new InvoiceLine(
        UUID.randomUUID(),
        invoice.getId(),
        1,
        null,
        "Séance de psychothérapie",
        LocalDate.of(2026, 11, 12),
        quantity,
        unitPrice,
        null);
  }

  private static BillingIssuer issuer(String regime, Integer rateBp, boolean bankDetails) {
    return new BillingIssuer(
        "Claire Moreau",
        "Psychologue",
        "75 93 0000 0",
        "73282932000074",
        "12 rue des Lilas\n75011 Paris",
        "claire@example.test",
        "01 00 00 00 00",
        regime,
        switch (regime) {
          case "health_exempt" -> "TVA non applicable, article 261-4-1° du Code général des impôts";
          case "franchise" -> "TVA non applicable, article 293 B du Code général des impôts";
          default -> null;
        },
        rateBp,
        "liable".equals(regime) ? "FR40303265045" : null,
        "Claire Moreau",
        List.of("transfer", "cheque", "cash"),
        bankDetails,
        "F",
        "on_receipt",
        null);
  }
}
