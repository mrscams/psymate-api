package com.psymate.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.billing.dto.BillingEmailTemplateRequest;
import com.psymate.billing.dto.BillingEmailTemplatesRequest;
import com.psymate.billing.dto.BillingNumberingRequest;
import com.psymate.billing.dto.BillingPaymentTermsRequest;
import com.psymate.billing.dto.BillingSettingsRequest;
import com.psymate.billing.dto.BillingVatRequest;
import com.psymate.identity.api.PractitionerFacade.IssuerView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Règles des réglages de facturation (M15-06, L5.1), sans base : missing, adresse, champs. */
class BillingSettingsServiceTest {

  private static final String SIRET = "73282932000074";

  @Test
  void missingListsSiretAddressAndServiceInThatOrder() {
    assertThat(BillingSettingsService.missing(null, null, false))
        .containsExactly("siret", "addressLines", "service");
    assertThat(BillingSettingsService.missing(" ", "", false))
        .containsExactly("siret", "addressLines", "service");
  }

  @Test
  void missingDropsWhatIsPresent() {
    assertThat(BillingSettingsService.missing(SIRET, null, true)).containsExactly("addressLines");
    assertThat(BillingSettingsService.missing(null, "12 rue des Lilas", true))
        .containsExactly("siret");
    assertThat(BillingSettingsService.missing(SIRET, "12 rue des Lilas", false))
        .containsExactly("service");
    assertThat(BillingSettingsService.missing(SIRET, "12 rue des Lilas", true)).isEmpty();
  }

  @Test
  void proposedAddressJoinsStreetThenPostalCodeAndCity() {
    assertThat(BillingSettingsService.proposedAddress(issuer("12 rue des Lilas", "75011", "Paris")))
        .isEqualTo("12 rue des Lilas\n75011 Paris");
    assertThat(BillingSettingsService.proposedAddress(issuer(null, "75011", "Paris")))
        .isEqualTo("75011 Paris");
    assertThat(BillingSettingsService.proposedAddress(issuer("12 rue des Lilas", " ", null)))
        .isEqualTo("12 rue des Lilas");
    assertThat(BillingSettingsService.proposedAddress(issuer(null, null, " "))).isNull();
    assertThat(BillingSettingsService.proposedAddress(null)).isNull();
  }

  @Test
  void nextNumberFollowsTheLastOne() {
    assertThat(BillingSettingsService.next(0)).isEqualTo(1);
    assertThat(BillingSettingsService.next(147)).isEqualTo(148);
  }

  @Test
  void emptyRequestTakesTheDefaults() {
    BillingSettingsService.Resolved r = resolve(request(null, null, null, null));
    assertThat(r.problems()).isEmpty();
    assertThat(r.siret()).isNull();
    assertThat(r.regime()).isEqualTo("health_exempt");
    assertThat(r.invoicePrefix()).isEqualTo("F");
    assertThat(r.creditNotePrefix()).isEqualTo("AV");
    assertThat(r.dueTerm()).isEqualTo("on_receipt");
    assertThat(r.acceptedModes()).containsExactly("transfer", "cheque", "cash");
    assertThat(r.showBankDetails()).isFalse();
  }

  @Test
  void siretIsNormalizedAndChecked() {
    assertThat(resolve(request(" 732 829 320 00074 ", null, null, null)).siret()).isEqualTo(SIRET);
    assertThat(codes(request("73282932000075", null, null, null)))
        .containsExactly("siret:SIRET_INVALID");
    assertThat(codes(request("1234", null, null, null))).containsExactly("siret:SIRET_INVALID");
  }

  @Test
  void liableRegimeRequiresRateAndNumber() {
    assertThat(codes(request(null, new BillingVatRequest("liable", null, null), null, null)))
        .containsExactly("vat.rateBp:VAT_RATE_REQUIRED", "vat.vatNumber:VAT_NUMBER_REQUIRED");
    BillingSettingsService.Resolved ok =
        resolve(
            request(null, new BillingVatRequest("liable", 2000, "fr 40 303 265 045"), null, null));
    assertThat(ok.problems()).isEmpty();
    assertThat(ok.vatNumber()).isEqualTo("FR40303265045");
    assertThat(
            codes(request(null, new BillingVatRequest("liable", 2000, "40303265045"), null, null)))
        .containsExactly("vat.vatNumber:INVALID_VALUE");
    assertThat(
            codes(
                request(
                    null,
                    new BillingVatRequest("liable", 2000, "FR" + "1".repeat(19)),
                    null,
                    null)))
        .containsExactly("vat.vatNumber:OUT_OF_RANGE");
  }

  @Test
  void exemptRegimesIgnoreRateAndNumber() {
    assertThat(codes(request(null, new BillingVatRequest("franchise", null, "x"), null, null)))
        .isEmpty();
    assertThat(codes(request(null, new BillingVatRequest("autre", null, null), null, null)))
        .containsExactly("vat.regime:INVALID_VALUE");
  }

  @Test
  void prefixesAreLettersAndDigitsAndDiffer() {
    assertThat(codes(request(null, null, new BillingNumberingRequest("FAC-2", "AVO_1"), null)))
        .isEmpty();
    assertThat(codes(request(null, null, new BillingNumberingRequest("F/", "A V"), null)))
        .containsExactly(
            "numbering.invoicePrefix:INVALID_VALUE", "numbering.creditNotePrefix:INVALID_VALUE");
    assertThat(codes(request(null, null, new BillingNumberingRequest("FA", "fa"), null)))
        .containsExactly("numbering.creditNotePrefix:INVALID_VALUE");
    assertThat(codes(request(null, null, new BillingNumberingRequest("AV", null), null)))
        .containsExactly("numbering.creditNotePrefix:INVALID_VALUE");
  }

  @Test
  void paymentTermsAreChecked() {
    assertThat(
            codes(
                request(
                    null,
                    null,
                    null,
                    new BillingPaymentTermsRequest("days_60", null, List.of(), true))))
        .containsExactly("payment.dueTerm:INVALID_VALUE", "payment.acceptedModes:INVALID_VALUE");
    assertThat(
            codes(
                request(
                    null,
                    null,
                    null,
                    new BillingPaymentTermsRequest(null, null, List.of("cash", "bitcoin"), null))))
        .containsExactly("payment.acceptedModes:INVALID_VALUE");
    BillingSettingsService.Resolved r =
        resolve(
            request(
                null,
                null,
                null,
                new BillingPaymentTermsRequest(
                    "days_30", " Cabinet ", List.of("card", "card"), true)));
    assertThat(r.acceptedModes()).containsExactly("card");
    assertThat(r.chequePayee()).isEqualTo("Cabinet");
    assertThat(r.showBankDetails()).isTrue();
  }

  @Test
  void contactValuesAreChecked() {
    BillingSettingsRequest bad =
        new BillingSettingsRequest(
            null, null, "pas-une-adresse", "06 12 ab", null, null, null, null, null);
    assertThat(codes(bad))
        .containsExactly("displayEmail:INVALID_VALUE", "displayPhone:INVALID_VALUE");
    BillingSettingsRequest good =
        new BillingSettingsRequest(
            null, "  ", "cabinet@example.test", "+33 (0)6 12.34-56", null, null, null, null, null);
    BillingSettingsService.Resolved r = resolve(good);
    assertThat(r.problems()).isEmpty();
    assertThat(r.addressLines()).isNull();
  }

  @Test
  void anEmailSubjectFitsOnOneLine() {
    BillingSettingsRequest request =
        new BillingSettingsRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new BillingEmailTemplatesRequest(
                new BillingEmailTemplateRequest("Facture\r\nBcc: x@example.test", "Corps"), null));
    assertThat(codes(request)).containsExactly("emailTemplates.invoice.subject:INVALID_VALUE");
  }

  // ---- outils

  private static BillingSettingsRequest request(
      String siret,
      BillingVatRequest vat,
      BillingNumberingRequest numbering,
      BillingPaymentTermsRequest payment) {
    return new BillingSettingsRequest(siret, null, null, null, vat, numbering, payment, null, null);
  }

  private static BillingSettingsService.Resolved resolve(BillingSettingsRequest request) {
    return BillingSettingsService.resolve(request);
  }

  private static List<String> codes(BillingSettingsRequest request) {
    return resolve(request).problems().stream().map(p -> p.field() + ":" + p.code()).toList();
  }

  private static IssuerView issuer(String street, String postalCode, String city) {
    return new IssuerView(
        UUID.randomUUID(),
        "Maya",
        "Kaddour",
        "Dr Maya Kaddour",
        "psychologist",
        null,
        street,
        postalCode,
        city,
        null,
        "maya@example.test");
  }
}
