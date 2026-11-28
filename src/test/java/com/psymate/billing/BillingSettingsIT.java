package com.psymate.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.EncryptedStorageFacade;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Réglages de facturation et IBAN (M15-06, L5.1) sur une vraie base (Testcontainers
 * pgvector/pgvector:pg16) : valeurs par défaut et propositions d'identity, mention de TVA en
 * français, remplacement, chaque code d'erreur de L5.1, verrou des préfixes après une émission
 * simulée par une ligne de {@code billing_counter}, IBAN rangé chiffré et absent de toute réponse,
 * de toute table et de tout journal, isolement entre praticiens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
@ExtendWith(OutputCaptureExtension.class)
class BillingSettingsIT {

  private static final String PASSWORD = "Tournesol-Bleu-2026";
  private static final String BOB_PASSWORD = "Autre-Tournesol-88";
  private static final String IBAN = "FR7630006000011234567890189";
  private static final String IBAN_TYPED = "fr76 3000 6000 0112 3456 7890 189";
  private static final String BBAN = "30006000011234567890189";
  private static final String OTHER_IBAN = "DE89370400440532013000";
  private static final String SIRET = "73282932000074";
  private static final String HEALTH_EXEMPT =
      "TVA non applicable, article 261-4-1° du Code général des impôts";
  private static final String FRANCHISE =
      "TVA non applicable, article 293 B du Code général des impôts";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordEncoder encoder;
  @Autowired private EncryptedStorageFacade storage;

  @Value("${app.storage.dir}")
  private String storageDir;

  private UUID tenant;
  private UUID alice;
  private UUID bob;
  private int year;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet réglages')", tenant);
    alice = practitioner(PASSWORD);
    bob = practitioner(BOB_PASSWORD);
    year = LocalDate.now(ZoneId.of("Europe/Paris")).getYear();
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
    storage.delete(key(alice));
    storage.delete(key(bob));
  }

  // ---- GET : valeurs par défaut

  @Test
  void withoutRowGetReturnsDefaultsAndIdentityProposals() throws Exception {
    get(alice)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.identity.name").value("Dr Alice Réglages"))
        .andExpect(jsonPath("$.identity.title").value("Psychologue"))
        .andExpect(jsonPath("$.identity.professionalNumber").value("689712345"))
        .andExpect(jsonPath("$.siret").value(nullValue()))
        .andExpect(jsonPath("$.addressLines").value("12 rue des Lilas\n75011 Paris"))
        .andExpect(jsonPath("$.displayEmail").value(alice + "@example.test"))
        .andExpect(jsonPath("$.displayPhone").value(nullValue()))
        .andExpect(jsonPath("$.vat.regime").value("health_exempt"))
        .andExpect(jsonPath("$.vat.rateBp").value(nullValue()))
        .andExpect(jsonPath("$.vat.vatNumber").value(nullValue()))
        .andExpect(jsonPath("$.vat.mention").value(HEALTH_EXEMPT))
        .andExpect(jsonPath("$.numbering.invoicePrefix").value("F"))
        .andExpect(jsonPath("$.numbering.creditNotePrefix").value("AV"))
        .andExpect(jsonPath("$.numbering.nextInvoiceNumber").value("F-" + year + "-0001"))
        .andExpect(jsonPath("$.numbering.nextCreditNoteNumber").value("AV-" + year + "-0001"))
        .andExpect(jsonPath("$.numbering.locked").value(false))
        .andExpect(jsonPath("$.payment.dueTerm").value("on_receipt"))
        .andExpect(jsonPath("$.payment.chequePayee").value(nullValue()))
        .andExpect(
            jsonPath("$.payment.acceptedModes").value(contains("transfer", "cheque", "cash")))
        .andExpect(jsonPath("$.payment.showBankDetails").value(false))
        .andExpect(jsonPath("$.payment.ibanLast4").value(nullValue()))
        .andExpect(jsonPath("$.footerNote").value(nullValue()))
        .andExpect(jsonPath("$.emailTemplates.invoice.subject").value("Facture {numéro}"))
        .andExpect(jsonPath("$.emailTemplates.invoice.body").isNotEmpty())
        .andExpect(jsonPath("$.emailTemplates.reminder.subject").value("Rappel : facture {numéro}"))
        .andExpect(jsonPath("$.emailTemplates.reminder.body").isNotEmpty())
        .andExpect(jsonPath("$.missing").value(contains("siret", "service")));
    assertThat(rows(alice)).isZero();
  }

  @Test
  void withoutAnyAddressMissingListsIt() throws Exception {
    jdbc.update(
        "update practitioner set address_street = null, address_postal_code = null,"
            + " address_city = null where id = ?",
        alice);
    get(alice)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.addressLines").value(nullValue()))
        .andExpect(jsonPath("$.missing").value(contains("siret", "addressLines", "service")));
  }

  @Test
  void mentionAndTemplatesStayFrenchWhileErrorsFollowTheLanguage() throws Exception {
    for (String language : List.of("en", "ar")) {
      send(MockMvcRequestBuilders.get("/api/me/billing-settings"), alice, null, language)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.vat.mention").value(HEALTH_EXEMPT))
          .andExpect(jsonPath("$.identity.title").value("Psychologue"))
          .andExpect(jsonPath("$.emailTemplates.invoice.subject").value("Facture {numéro}"));
    }
    String fr = errorMessage(null);
    String en = errorMessage("en");
    String ar = errorMessage("ar");
    assertThat(fr).contains("SIRET").isNotEqualTo(en);
    assertThat(en).contains("SIRET").isNotEqualTo(ar);
    assertThat(ar).contains("SIRET");
  }

  // ---- PUT

  @Test
  void putStoresEverySettingAndGetReadsIt() throws Exception {
    put(
            alice,
            "{\"siret\":\" 732 829 320 00074 \",\"addressLines\":\"  3 place du Marché\\n69001 Lyon \","
                + "\"displayEmail\":\"cabinet@example.test\",\"displayPhone\":\"+33 6 12 34 56 78\","
                + "\"vat\":{\"regime\":\"liable\",\"rateBp\":2000,\"vatNumber\":\"fr 40 303 265 045\"},"
                + "\"numbering\":{\"invoicePrefix\":\"FAC\",\"creditNotePrefix\":\"AVO\"},"
                + "\"payment\":{\"dueTerm\":\"days_30\",\"chequePayee\":\"Cabinet Réglages\","
                + "\"acceptedModes\":[\"card\",\"transfer\",\"card\"],\"showBankDetails\":true},"
                + "\"footerNote\":\"Membre d'une association agréée.\","
                + "\"emailTemplates\":{\"invoice\":{\"subject\":\"Votre facture {numéro}\","
                + "\"body\":\"Bonjour, voici la facture {numéro}.\"}}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.siret").value(SIRET))
        .andExpect(jsonPath("$.addressLines").value("3 place du Marché\n69001 Lyon"))
        .andExpect(jsonPath("$.displayEmail").value("cabinet@example.test"))
        .andExpect(jsonPath("$.displayPhone").value("+33 6 12 34 56 78"))
        .andExpect(jsonPath("$.vat.regime").value("liable"))
        .andExpect(jsonPath("$.vat.rateBp").value(2000))
        .andExpect(jsonPath("$.vat.vatNumber").value("FR40303265045"))
        .andExpect(jsonPath("$.vat.mention").value(nullValue()))
        .andExpect(jsonPath("$.numbering.nextInvoiceNumber").value("FAC-" + year + "-0001"))
        .andExpect(jsonPath("$.numbering.nextCreditNoteNumber").value("AVO-" + year + "-0001"))
        .andExpect(jsonPath("$.payment.dueTerm").value("days_30"))
        .andExpect(jsonPath("$.payment.chequePayee").value("Cabinet Réglages"))
        .andExpect(jsonPath("$.payment.acceptedModes").value(contains("transfer", "card")))
        .andExpect(jsonPath("$.payment.showBankDetails").value(true))
        .andExpect(jsonPath("$.footerNote").value("Membre d'une association agréée."))
        .andExpect(jsonPath("$.emailTemplates.invoice.subject").value("Votre facture {numéro}"))
        .andExpect(jsonPath("$.emailTemplates.reminder.subject").value("Rappel : facture {numéro}"))
        .andExpect(jsonPath("$.missing").value(contains("service")));

    jdbc.update(
        "insert into billing_service (id, tenant_id, practitioner_id, label, price_cents,"
            + " is_default) values (?, ?, ?, 'Séance', 6000, true)",
        UUID.randomUUID(),
        tenant,
        alice);
    get(alice)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.siret").value(SIRET))
        .andExpect(jsonPath("$.numbering.invoicePrefix").value("FAC"))
        .andExpect(jsonPath("$.missing").value(empty()));

    Map<String, Object> row =
        jdbc.queryForMap(
            "select tenant_id, siret, vat_regime, vat_rate_bp, vat_number, accepted_modes::text"
                + " as modes, email_templates::text as templates from billing_settings"
                + " where practitioner_id = ?",
            alice);
    assertThat(row.get("tenant_id")).isEqualTo(tenant);
    assertThat(row.get("siret")).isEqualTo(SIRET);
    assertThat(row.get("vat_regime")).isEqualTo("liable");
    assertThat(row.get("vat_rate_bp")).isEqualTo(2000);
    assertThat(row.get("vat_number")).isEqualTo("FR40303265045");
    assertThat((String) row.get("modes")).isEqualTo("[\"transfer\", \"card\"]");
    assertThat((String) row.get("templates"))
        .contains("Votre facture {numéro}")
        .doesNotContain("reminder");
  }

  @Test
  void putReplacesEverythingNullBlocksTakeTheDefaults() throws Exception {
    put(
            alice,
            "{\"siret\":\""
                + SIRET
                + "\",\"displayPhone\":\"0612\",\"vat\":{\"regime\":\"liable\","
                + "\"rateBp\":550,\"vatNumber\":\"FR40303265045\"},\"payment\":{\"dueTerm\":\"days_15\"},"
                + "\"footerNote\":\"Note\"}")
        .andExpect(status().isOk());

    put(alice, "{\"vat\":{\"regime\":\"franchise\",\"rateBp\":2000},\"footerNote\":\"  \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.siret").value(nullValue()))
        .andExpect(jsonPath("$.displayPhone").value(nullValue()))
        .andExpect(jsonPath("$.footerNote").value(nullValue()))
        .andExpect(jsonPath("$.vat.regime").value("franchise"))
        .andExpect(jsonPath("$.vat.rateBp").value(nullValue()))
        .andExpect(jsonPath("$.vat.vatNumber").value(nullValue()))
        .andExpect(jsonPath("$.vat.mention").value(FRANCHISE))
        .andExpect(jsonPath("$.payment.dueTerm").value("on_receipt"))
        .andExpect(
            jsonPath("$.payment.acceptedModes").value(contains("transfer", "cheque", "cash")));

    put(alice, "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vat.regime").value("health_exempt"))
        .andExpect(jsonPath("$.vat.mention").value(HEALTH_EXEMPT))
        .andExpect(jsonPath("$.addressLines").value("12 rue des Lilas\n75011 Paris"));
    assertThat(rows(alice)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select address_lines is null and display_email is null from billing_settings"
                    + " where practitioner_id = ?",
                Boolean.class,
                alice))
        .as("une proposition d'identity n'est pas enregistrée")
        .isTrue();
  }

  @Test
  void templatesEqualToTheDefaultAreNotStoredAndLongTextsAreRefused() throws Exception {
    put(
            alice,
            "{\"emailTemplates\":{\"invoice\":{\"subject\":\"Facture {numéro}\"},"
                + "\"reminder\":{\"subject\":\" \",\"body\":null}}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailTemplates.invoice.subject").value("Facture {numéro}"));
    assertThat(
            jdbc.queryForObject(
                "select email_templates::text from billing_settings where practitioner_id = ?",
                String.class,
                alice))
        .isEqualTo("{}");

    expectFieldError(
        put(alice, "{\"emailTemplates\":{\"invoice\":{\"subject\":\"" + "x".repeat(201) + "\"}}}"),
        "emailTemplates.invoice.subject",
        "OUT_OF_RANGE");
    expectFieldError(
        put(alice, "{\"emailTemplates\":{\"reminder\":{\"body\":\"" + "x".repeat(5001) + "\"}}}"),
        "emailTemplates.reminder.body",
        "OUT_OF_RANGE");
    put(alice, "{\"emailTemplates\":{\"reminder\":{\"body\":\"" + "x".repeat(5000) + "\"}}}")
        .andExpect(status().isOk());
  }

  // ---- codes d'erreur de L5.1

  @Test
  void siretInvalidWhenNotFourteenDigitsOrWrongLuhnKey() throws Exception {
    expectFieldError(put(alice, "{\"siret\":\"73282932000075\"}"), "siret", "SIRET_INVALID");
    expectFieldError(put(alice, "{\"siret\":\"7328293200007\"}"), "siret", "SIRET_INVALID");
    expectFieldError(put(alice, "{\"siret\":\"7328293200007A\"}"), "siret", "SIRET_INVALID");
    assertThat(rows(alice)).isZero();
  }

  @Test
  void vatRateRequiredForTheLiableRegime() throws Exception {
    expectFieldError(
        put(alice, "{\"vat\":{\"regime\":\"liable\",\"vatNumber\":\"FR40303265045\"}}"),
        "vat.rateBp",
        "VAT_RATE_REQUIRED");
    assertThat(rows(alice)).isZero();
  }

  @Test
  void vatNumberRequiredForTheLiableRegime() throws Exception {
    expectFieldError(
        put(alice, "{\"vat\":{\"regime\":\"liable\",\"rateBp\":2000,\"vatNumber\":\" \"}}"),
        "vat.vatNumber",
        "VAT_NUMBER_REQUIRED");
    assertThat(rows(alice)).isZero();
  }

  @Test
  void outOfRangeValuesAreRefused() throws Exception {
    expectFieldError(
        put(alice, "{\"vat\":{\"regime\":\"liable\",\"rateBp\":10001,\"vatNumber\":\"FR1\"}}"),
        "vat.rateBp",
        "OUT_OF_RANGE");
    expectFieldError(
        put(alice, "{\"footerNote\":\"" + "x".repeat(501) + "\"}"), "footerNote", "OUT_OF_RANGE");
    expectFieldError(
        put(alice, "{\"numbering\":{\"invoicePrefix\":\"" + "F".repeat(11) + "\"}}"),
        "numbering.invoicePrefix",
        "OUT_OF_RANGE");
    expectFieldError(
        put(alice, "{\"addressLines\":\"" + "x".repeat(301) + "\"}"),
        "addressLines",
        "OUT_OF_RANGE");
    assertThat(rows(alice)).isZero();
  }

  @Test
  void invalidValuesAreRefused() throws Exception {
    expectFieldError(
        put(alice, "{\"vat\":{\"regime\":\"exempt\"}}"), "vat.regime", "INVALID_VALUE");
    expectFieldError(
        put(alice, "{\"payment\":{\"dueTerm\":\"days_60\"}}"), "payment.dueTerm", "INVALID_VALUE");
    expectFieldError(
        put(alice, "{\"payment\":{\"acceptedModes\":[]}}"),
        "payment.acceptedModes",
        "INVALID_VALUE");
    expectFieldError(
        put(alice, "{\"numbering\":{\"invoicePrefix\":\"AV\"}}"),
        "numbering.creditNotePrefix",
        "INVALID_VALUE");
    expectFieldError(put(alice, "{\"displayEmail\":\"cabinet\"}"), "displayEmail", "INVALID_VALUE");
    assertThat(rows(alice)).isZero();
  }

  @Test
  void allFieldProblemsAreReportedTogether() throws Exception {
    put(alice, "{\"siret\":\"1\",\"vat\":{\"regime\":\"liable\"},\"payment\":{\"dueTerm\":\"x\"}}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(contains("siret", "vat.rateBp", "vat.vatNumber", "payment.dueTerm")))
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(
                    contains(
                        "SIRET_INVALID",
                        "VAT_RATE_REQUIRED",
                        "VAT_NUMBER_REQUIRED",
                        "INVALID_VALUE")));
  }

  @Test
  void decimalsReadOnlyAndUnknownFieldsAreRefused() throws Exception {
    expectFieldError(
        put(alice, "{\"vat\":{\"regime\":\"liable\",\"rateBp\":20.5}}"),
        "vat.rateBp",
        "INVALID_FORMAT");
    expectFieldError(put(alice, "{\"missing\":[]}"), "missing", "UNKNOWN_FIELD");
    expectFieldError(
        put(alice, "{\"vat\":{\"mention\":\"TVA offerte\"}}"), "vat.mention", "UNKNOWN_FIELD");
    expectFieldError(
        put(alice, "{\"payment\":{\"ibanLast4\":\"0189\"}}"), "payment.ibanLast4", "UNKNOWN_FIELD");
    expectFieldError(
        put(alice, "{\"practitionerId\":\"" + bob + "\"}"), "practitionerId", "UNKNOWN_FIELD");
    assertThat(rows(alice)).isZero();
  }

  @Test
  void prefixesLockAfterACounterRowAndOtherChangesStillPass() throws Exception {
    put(alice, "{\"numbering\":{\"invoicePrefix\":\"FAC\",\"creditNotePrefix\":\"AVO\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.numbering.locked").value(false));
    // Émission simulée : une ligne de billing_counter, comme l'écrira M15-09.
    jdbc.update(
        "insert into billing_counter (practitioner_id, kind, year, last_number)"
            + " values (?, 'invoice', ?, 147)",
        alice,
        year);

    get(alice)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.numbering.locked").value(true))
        .andExpect(jsonPath("$.numbering.nextInvoiceNumber").value("FAC-" + year + "-0148"))
        .andExpect(jsonPath("$.numbering.nextCreditNoteNumber").value("AVO-" + year + "-0001"));

    put(alice, "{\"numbering\":{\"invoicePrefix\":\"F\",\"creditNotePrefix\":\"AVO\"}}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NUMBERING_LOCKED"))
        .andExpect(jsonPath("$.detail").isNotEmpty());
    put(alice, "{\"siret\":\"" + SIRET + "\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NUMBERING_LOCKED"));
    put(
            alice,
            "{\"siret\":\""
                + SIRET
                + "\",\"numbering\":{\"invoicePrefix\":\"FAC\",\"creditNotePrefix\":\"AVO\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.siret").value(SIRET))
        .andExpect(jsonPath("$.numbering.invoicePrefix").value("FAC"));
    assertThat(
            jdbc.queryForObject(
                "select invoice_prefix from billing_settings where practitioner_id = ?",
                String.class,
                alice))
        .isEqualTo("FAC");
  }

  @Test
  void aCounterOfAnotherYearOrKindLocksTooButAZeroCounterDoesNot() throws Exception {
    jdbc.update(
        "insert into billing_counter (practitioner_id, kind, year, last_number)"
            + " values (?, 'credit_note', ?, 0)",
        alice,
        year);
    get(alice).andExpect(jsonPath("$.numbering.locked").value(false));
    jdbc.update(
        "insert into billing_counter (practitioner_id, kind, year, last_number)"
            + " values (?, 'credit_note', ?, 3)",
        alice,
        year - 1);
    get(alice)
        .andExpect(jsonPath("$.numbering.locked").value(true))
        .andExpect(jsonPath("$.numbering.nextCreditNoteNumber").value("AV-" + year + "-0001"));
    put(alice, "{\"numbering\":{\"creditNotePrefix\":\"A\"}}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NUMBERING_LOCKED"));
  }

  // ---- IBAN

  @Test
  void ibanIsStoredEncryptedOnlyItsLastFourCharactersAreReadable(CapturedOutput output)
      throws Exception {
    putIban(alice, "{\"iban\":\"" + IBAN_TYPED + "\",\"currentPassword\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());

    String body =
        get(alice)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payment.ibanLast4").value("0189"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).doesNotContain(IBAN).doesNotContain(BBAN).doesNotContain("FR76 3000");
    String putBody =
        put(alice, "{\"siret\":\"" + SIRET + "\"}")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(putBody).doesNotContain(IBAN).doesNotContain(BBAN);

    assertThat(
            jdbc.queryForObject(
                "select iban_last4 from billing_settings where practitioner_id = ?",
                String.class,
                alice))
        .isEqualTo("0189");
    // Rangé chiffré : la façade rend l'IBAN normalisé, le fichier ne le contient pas en clair.
    assertThat(new String(storage.get(key(alice)), StandardCharsets.UTF_8)).isEqualTo(IBAN);
    byte[] raw = Files.readAllBytes(Path.of(storageDir).resolve(key(alice)));
    assertThat(new String(raw, StandardCharsets.ISO_8859_1))
        .doesNotContain(IBAN)
        .doesNotContain(BBAN);
    assertThat(tablesContaining(IBAN)).as("tables contenant l'IBAN").isEmpty();
    assertThat(tablesContaining(BBAN)).as("tables contenant le BBAN").isEmpty();
    assertThat(output.getAll()).doesNotContain(IBAN).doesNotContain(BBAN).doesNotContain(PASSWORD);
  }

  @Test
  void anotherIbanReplacesTheFirst() throws Exception {
    putIban(alice, "{\"iban\":\"" + IBAN + "\",\"currentPassword\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    putIban(alice, "{\"iban\":\"" + OTHER_IBAN + "\",\"currentPassword\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    get(alice).andExpect(jsonPath("$.payment.ibanLast4").value("3000"));
    assertThat(new String(storage.get(key(alice)), StandardCharsets.UTF_8)).isEqualTo(OTHER_IBAN);
  }

  @Test
  void ibanInvalidWhenTheFormatOrTheMod97KeyIsWrong() throws Exception {
    expectFieldError(
        putIban(
            alice,
            "{\"iban\":\"FR7630006000011234567890188\",\"currentPassword\":\"" + PASSWORD + "\"}"),
        "iban",
        "IBAN_INVALID");
    expectFieldError(
        putIban(alice, "{\"iban\":\"FR76-3000-6000\",\"currentPassword\":\"" + PASSWORD + "\"}"),
        "iban",
        "IBAN_INVALID");
    expectFieldError(
        putIban(alice, "{\"currentPassword\":\"" + PASSWORD + "\"}"), "iban", "REQUIRED");
    expectFieldError(putIban(alice, "{\"iban\":\"" + IBAN + "\"}"), "currentPassword", "REQUIRED");
    assertThat(storage.exists(key(alice))).isFalse();
    assertThat(rows(alice)).isZero();
  }

  @Test
  void passwordInvalidWritesNothing(CapturedOutput output) throws Exception {
    putIban(alice, "{\"iban\":\"" + IBAN + "\",\"currentPassword\":\"mauvais\"}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_INVALID"))
        .andExpect(jsonPath("$.detail").isNotEmpty());
    assertThat(storage.exists(key(alice))).isFalse();
    assertThat(rows(alice)).isZero();
    assertThat(output.getAll()).doesNotContain(IBAN).doesNotContain("mauvais");
  }

  @Test
  void deleteRequiresThePasswordThenErasesTheIbanAndIsIdempotent() throws Exception {
    putIban(alice, "{\"iban\":\"" + IBAN + "\",\"currentPassword\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());

    deleteIban(alice, "{\"currentPassword\":\"mauvais\"}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_INVALID"));
    expectFieldError(deleteIban(alice, "{}"), "currentPassword", "REQUIRED");
    assertThat(storage.exists(key(alice))).isTrue();

    deleteIban(alice, "{\"currentPassword\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    assertThat(storage.exists(key(alice))).isFalse();
    get(alice).andExpect(jsonPath("$.payment.ibanLast4").value(nullValue()));
    assertThat(
            jdbc.queryForObject(
                "select iban_last4 from billing_settings where practitioner_id = ?",
                String.class,
                alice))
        .isNull();
    deleteIban(alice, "{\"currentPassword\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
  }

  // ---- isolement

  @Test
  void practitionersNeverSeeOrChangeEachOthersSettings() throws Exception {
    put(alice, "{\"siret\":\"" + SIRET + "\",\"numbering\":{\"invoicePrefix\":\"FAC\"}}")
        .andExpect(status().isOk());
    putIban(alice, "{\"iban\":\"" + IBAN + "\",\"currentPassword\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    jdbc.update(
        "insert into billing_counter (practitioner_id, kind, year, last_number)"
            + " values (?, 'invoice', ?, 5)",
        bob,
        year);

    get(bob)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.siret").value(nullValue()))
        .andExpect(jsonPath("$.numbering.invoicePrefix").value("F"))
        .andExpect(jsonPath("$.numbering.nextInvoiceNumber").value("F-" + year + "-0006"))
        .andExpect(jsonPath("$.numbering.locked").value(true))
        .andExpect(jsonPath("$.payment.ibanLast4").value(nullValue()))
        .andExpect(jsonPath("$.displayEmail").value(bob + "@example.test"));
    get(alice)
        .andExpect(jsonPath("$.numbering.locked").value(false))
        .andExpect(jsonPath("$.numbering.nextInvoiceNumber").value("FAC-" + year + "-0001"));

    // Le mot de passe d'un autre praticien ne vaut rien ; l'IBAN de Bob ne touche pas Alice.
    putIban(alice, "{\"iban\":\"" + OTHER_IBAN + "\",\"currentPassword\":\"" + BOB_PASSWORD + "\"}")
        .andExpect(status().isForbidden());
    putIban(bob, "{\"iban\":\"" + OTHER_IBAN + "\",\"currentPassword\":\"" + BOB_PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    put(bob, "{\"siret\":\"44306184100047\"}").andExpect(status().isOk());
    deleteIban(bob, "{\"currentPassword\":\"" + BOB_PASSWORD + "\"}")
        .andExpect(status().isNoContent());

    get(alice)
        .andExpect(jsonPath("$.siret").value(SIRET))
        .andExpect(jsonPath("$.payment.ibanLast4").value("0189"));
    assertThat(new String(storage.get(key(alice)), StandardCharsets.UTF_8)).isEqualTo(IBAN);
    assertThat(storage.exists(key(bob))).isFalse();
  }

  @Test
  void fivePasswordFailuresBlockTheIbanRoutesEvenWithTheRightPassword() throws Exception {
    for (int i = 0; i < 5; i++) {
      String body = "{\"iban\":\"" + IBAN + "\",\"currentPassword\":\"mauvais-" + i + "\"}";
      ResultActions attempt =
          i % 2 == 0
              ? putIban(alice, body)
              : deleteIban(alice, "{\"currentPassword\":\"x" + i + "\"}");
      attempt
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("PASSWORD_INVALID"));
    }

    String retryAfter =
        putIban(alice, "{\"iban\":\"" + IBAN + "\",\"currentPassword\":\"" + PASSWORD + "\"}")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
            .andExpect(jsonPath("$.detail").isNotEmpty())
            .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
            .andReturn()
            .getResponse()
            .getHeader("Retry-After");
    assertThat(Long.parseLong(retryAfter)).isBetween(1L, 15 * 60L);
    deleteIban(alice, "{\"currentPassword\":\"" + PASSWORD + "\"}")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    // Un IBAN invalide ne passe pas avant le plafond : 429 aussi.
    putIban(alice, "{\"iban\":\"FR76\",\"currentPassword\":\"" + PASSWORD + "\"}")
        .andExpect(status().isTooManyRequests());
    assertThat(storage.exists(key(alice))).isFalse();
    assertThat(rows(alice)).isZero();

    // Un autre praticien n'est pas touché.
    putIban(bob, "{\"iban\":\"" + OTHER_IBAN + "\",\"currentPassword\":\"" + BOB_PASSWORD + "\"}")
        .andExpect(status().isNoContent());
  }

  @Test
  void aRightPasswordResetsTheFailureCount() throws Exception {
    for (int i = 0; i < 4; i++) {
      putIban(alice, "{\"iban\":\"" + IBAN + "\",\"currentPassword\":\"mauvais\"}")
          .andExpect(status().isForbidden());
    }
    putIban(alice, "{\"iban\":\"" + IBAN + "\",\"currentPassword\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    for (int i = 0; i < 4; i++) {
      deleteIban(alice, "{\"currentPassword\":\"mauvais\"}").andExpect(status().isForbidden());
    }
    deleteIban(alice, "{\"currentPassword\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
  }

  @Test
  void anonymousRequestsAreRefusedAndStoreNothing() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/me/billing-settings"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(
            MockMvcRequestBuilders.put("/api/me/billing-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"siret\":\"" + SIRET + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(
            MockMvcRequestBuilders.put("/api/me/billing-settings/iban")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"iban\":\"" + IBAN + "\",\"currentPassword\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(
            MockMvcRequestBuilders.delete("/api/me/billing-settings/iban")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    assertThat(tablesContaining(IBAN)).isEmpty();
    assertThat(storage.exists(key(alice))).isFalse();
    assertThat(rows(alice)).isZero();
  }

  // ---- outils

  private UUID practitioner(String password) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, professional_number,"
            + " address_street, address_postal_code, address_city)"
            + " values (?, ?, ?, ?, ?, 'Alice', 'Réglages', 'Dr Alice Réglages', 'psychologist',"
            + " '689712345', '12 rue des Lilas', '75011', 'Paris')",
        id,
        tenant,
        email,
        email,
        encoder.encode(password));
    return id;
  }

  private static String key(UUID practitionerId) {
    return "billing/iban/" + practitionerId;
  }

  private int rows(UUID practitionerId) {
    return jdbc.queryForObject(
        "select count(*) from billing_settings where practitioner_id = ?",
        Integer.class,
        practitionerId);
  }

  /** Tables du schéma public dont une ligne, lue en texte, contient {@code needle}. */
  private List<String> tablesContaining(String needle) {
    List<String> tables =
        jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'"
                + " and table_type = 'BASE TABLE'",
            String.class);
    return tables.stream()
        .filter(
            t ->
                jdbc.queryForObject(
                        "select count(*) from \"" + t + "\" x where x::text like ?",
                        Integer.class,
                        "%" + needle + "%")
                    > 0)
        .toList();
  }

  private String errorMessage(String language) throws Exception {
    return send(
            MockMvcRequestBuilders.put("/api/me/billing-settings"),
            alice,
            "{\"siret\":\"1\"}",
            language)
        .andExpect(status().isUnprocessableContent())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8)
        .replaceAll("(?s).*\"message\":\"([^\"]*)\".*", "$1");
  }

  private static void expectFieldError(ResultActions result, String field, String code)
      throws Exception {
    result
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].code").value(code))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
  }

  private RequestPostProcessor as(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            tenant,
            practitionerId + "@example.test",
            "Dr Alice Réglages",
            "psychologist",
            "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions send(
      MockHttpServletRequestBuilder builder, UUID who, String body, String language)
      throws Exception {
    if (body != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.getBytes(StandardCharsets.UTF_8));
    }
    if (language != null) {
      builder.header("Accept-Language", language);
    }
    return mvc.perform(builder.with(as(who)));
  }

  private ResultActions get(UUID who) throws Exception {
    return send(MockMvcRequestBuilders.get("/api/me/billing-settings"), who, null, null);
  }

  private ResultActions put(UUID who, String body) throws Exception {
    return send(MockMvcRequestBuilders.put("/api/me/billing-settings"), who, body, null);
  }

  private ResultActions putIban(UUID who, String body) throws Exception {
    return send(MockMvcRequestBuilders.put("/api/me/billing-settings/iban"), who, body, null);
  }

  private ResultActions deleteIban(UUID who, String body) throws Exception {
    return send(MockMvcRequestBuilders.delete("/api/me/billing-settings/iban"), who, body, null);
  }
}
