package com.psymate.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.EncryptedStorageFacade;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Émission, numérotation, PDF et duplication (M15-09, L5.3 de issue à duplicate ; G Q88, Q89, Q90,
 * Q92, Q93) sur une vraie base (Testcontainers pgvector/pgvector:pg16) : chaque route et chaque
 * code, numéro attendu, suite par année de la date d'émission, instantané de l'émetteur, paiement à
 * l'émission, immuabilité d'une facture émise, PDF rangé chiffré et servi tel quel, texte du PDF
 * extrait par PDFBox. La concurrence du compteur est dans {@link InvoiceIssueConcurrencyIT}.
 *
 * <p>Réglages, prestations et séances sont écrits en SQL (tables du module) ; le jour de Paris est
 * lu sur l'horloge du contexte, celle du serveur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class InvoiceIssueIT {

  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
  private static final Duration WAIT = Duration.ofSeconds(30);
  private static final String INVOICES = "/api/invoices";
  private static final DateTimeFormatter LINE_DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);
  private static final String SIRET = "73282932000074";
  private static final String HEALTH_EXEMPT =
      "TVA non applicable, article 261-4-1° du Code général des impôts";
  private static final String FRANCHISE =
      "TVA non applicable, article 293 B du Code général des impôts";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;
  @Autowired private EncryptedStorageFacade storage;

  @Value("${app.storage.dir}")
  private String storageDir;

  private UUID tenant;
  private UUID alice;
  private UUID bob;
  private UUID theo;
  private LocalDate today;
  private int year;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet émission')", tenant);
    alice = practitioner();
    bob = practitioner();
    theo = patient(alice, "Théo B.", "Théo", "Bernard");
    today = LocalDate.now(clock.withZone(PARIS));
    year = today.getYear();
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- POST /api/invoices/{id}/issue

  @Test
  void issuesADraftWithItsNumberSnapshotDueDateAndHistory() throws Exception {
    settings(alice);
    service(alice, "Séance individuelle", 6000, true);
    UUID session = row(alice, theo, today.minusDays(3), "pending");
    UUID draft =
        draft(alice, "{\"patientId\":\"" + theo + "\",\"sessionEntryIds\":[\"" + session + "\"]}");
    get(alice, draft).andExpect(jsonPath("$.nextNumberPreview").value(number(year, 1)));

    issue(alice, draft, "{\"expectedNumber\":\"" + number(year, 1) + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(draft.toString()))
        .andExpect(jsonPath("$.number").value(number(year, 1)))
        .andExpect(jsonPath("$.status").value("due"))
        .andExpect(jsonPath("$.overdue").value(false))
        .andExpect(jsonPath("$.issueDate").value(today.toString()))
        .andExpect(jsonPath("$.dueTerm").value("on_receipt"))
        .andExpect(jsonPath("$.dueOn").value(today.toString()))
        .andExpect(jsonPath("$.total").value(6000))
        .andExpect(jsonPath("$.subtotal").value(6000))
        .andExpect(jsonPath("$.balanceDue").value(6000))
        .andExpect(jsonPath("$.nextNumberPreview").value(nullValue()))
        .andExpect(jsonPath("$.issuer.name").value("Dr Facturation"))
        .andExpect(jsonPath("$.issuer.title").value("Psychologue"))
        .andExpect(jsonPath("$.issuer.siret").value(SIRET))
        .andExpect(jsonPath("$.issuer.addressLines").value("12 rue des Lilas\n75011 Paris"))
        .andExpect(jsonPath("$.issuer.vatMention").value(HEALTH_EXEMPT))
        .andExpect(jsonPath("$.recipient.name").value("Théo Bernard"))
        .andExpect(jsonPath("$.payments", hasSize(0)))
        .andExpect(jsonPath("$.history", hasSize(2)))
        .andExpect(jsonPath("$.history[0].kind").value("draft_created"))
        .andExpect(jsonPath("$.history[1].kind").value("issued"))
        .andExpect(jsonPath("$.history[1].detail.number").value(number(year, 1)));

    // Colonnes figées : statut, numéro, échéance, instantané, montants, PDF ; compteur à 1.
    assertThat(
            jdbc.queryForObject(
                "select status || '|' || number || '|' || due_on || '|' || total_cents || '|'"
                    + " || (issuer_snapshot->>'vatRegime') || '|' || (issued_at is not null)"
                    + " from invoice where id = ?",
                String.class,
                draft))
        .isEqualTo("issued|" + number(year, 1) + "|" + today + "|6000|health_exempt|true");
    assertThat(counter(alice, year)).isEqualTo(1);
    // Les séances restent facturées.
    assertThat(state(session)).isEqualTo("invoiced");
    // PDF rangé chiffré sous billing/invoice/{id}.pdf : lisible par la façade, pas en clair.
    String key = "billing/invoice/" + draft + ".pdf";
    assertThat(
            jdbc.queryForObject(
                "select pdf_storage_key from invoice where id = ?", String.class, draft))
        .isEqualTo(key);
    assertThat(new String(storage.get(key), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    byte[] raw = Files.readAllBytes(Path.of(storageDir).resolve(key));
    assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");
    // La liste montre la facture émise ; le prochain numéro avance.
    list(alice, "?status=due")
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].number").value(number(year, 1)));
    perform(MockMvcRequestBuilders.get("/api/me/billing-settings"), alice, null)
        .andExpect(jsonPath("$.numbering.nextInvoiceNumber").value(number(year, 2)))
        .andExpect(jsonPath("$.numbering.locked").value(true));
  }

  @Test
  void theDueDateFollowsTheTermOfTheDraftAndIsStored() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID thirty = freeDraft(alice, theo, 5000);
    patch(alice, thirty, "{\"dueTerm\":\"days_30\"}").andExpect(status().isOk());
    issue(alice, thirty, expected(year, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dueTerm").value("days_30"))
        .andExpect(jsonPath("$.dueOn").value(today.plusDays(30).toString()));
    UUID chosen = freeDraft(alice, theo, 5000);
    patch(alice, chosen, "{\"dueTerm\":\"date\",\"dueOn\":\"" + today.plusDays(7) + "\"}")
        .andExpect(status().isOk());
    issue(alice, chosen, expected(year, 2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dueOn").value(today.plusDays(7).toString()));
    assertThat(
            jdbc.queryForObject("select due_on from invoice where id = ?", Date.class, thirty)
                .toLocalDate())
        .isEqualTo(today.plusDays(30));
  }

  @Test
  void invoiceNumberChangedCarriesTheRightNumberAndIssuesNothing() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID first = freeDraft(alice, theo, 5000);
    UUID second = freeDraft(alice, theo, 7000);
    issue(alice, first, expected(year, 1)).andExpect(status().isOk());

    // Le second onglet affichait encore le premier numéro.
    issue(alice, second, expected(year, 1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVOICE_NUMBER_CHANGED"))
        .andExpect(jsonPath("$.nextNumber").value(number(year, 2)))
        .andExpect(jsonPath("$.detail").isNotEmpty());
    assertThat(storedStatus(second)).isEqualTo("draft");
    assertThat(counter(alice, year)).isEqualTo(1);
    assertThat(eventKinds(second)).containsExactly("draft_created");
    assertThat(storage.exists("billing/invoice/" + second + ".pdf")).isFalse();

    issue(alice, second, expected(year, 2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value(number(year, 2)));
    assertThat(counter(alice, year)).isEqualTo(2);
  }

  @Test
  void theSeriesRestartsAt0001WhenTheYearOfTheIssueDateChanges() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    int last = year - 1;
    // Année précédente : sept factures émises, la dernière le 15 décembre.
    issuedInSql(alice, theo, number(last, 7), LocalDate.of(last, 12, 15));
    jdbc.update(
        "insert into billing_counter (practitioner_id, kind, year, last_number)"
            + " values (?, 'invoice', ?, 7)",
        alice,
        last);

    // Une facture de l'année en cours ouvre sa propre suite à 0001.
    UUID current = freeDraft(alice, theo, 5000);
    get(alice, current).andExpect(jsonPath("$.nextNumberPreview").value(number(year, 1)));
    issue(alice, current, expected(year, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value(number(year, 1)));

    // Une facture datée de l'année précédente continue la suite de cette année-là.
    UUID late = freeDraft(alice, theo, 5000);
    patch(alice, late, "{\"issueDate\":\"" + LocalDate.of(last, 12, 31) + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextNumberPreview").value(number(last, 8)));
    issue(alice, late, expected(last, 8))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value(number(last, 8)));

    assertThat(counter(alice, year)).isEqualTo(1);
    assertThat(counter(alice, last)).isEqualTo(8);
  }

  @Test
  void aPastIssueDateIsAllowedButNeitherAFutureOneNorOneBeforeTheLastIssued() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    int last = year - 1;
    issuedInSql(alice, theo, number(last, 1), LocalDate.of(last, 12, 20));
    jdbc.update(
        "insert into billing_counter (practitioner_id, kind, year, last_number)"
            + " values (?, 'invoice', ?, 1)",
        alice,
        last);

    UUID future = freeDraft(alice, theo, 5000);
    patch(alice, future, "{\"issueDate\":\"" + today.plusDays(1) + "\"}")
        .andExpect(status().isOk());
    expectFieldError(issue(alice, future, expected(year, 1)), "issueDate", "OUT_OF_RANGE");

    UUID before = freeDraft(alice, theo, 5000);
    patch(alice, before, "{\"issueDate\":\"" + LocalDate.of(last, 12, 10) + "\"}")
        .andExpect(status().isOk());
    expectFieldError(issue(alice, before, expected(last, 2)), "issueDate", "INVALID_VALUE");
    assertThat(storedStatus(before)).isEqualTo("draft");

    // Même jour que la dernière émise : permis (date passée).
    patch(alice, before, "{\"issueDate\":\"" + LocalDate.of(last, 12, 20) + "\"}")
        .andExpect(status().isOk());
    issue(alice, before, expected(last, 2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issueDate").value(LocalDate.of(last, 12, 20).toString()));
  }

  @Test
  void incompleteSettingsAnswer422WithOneErrorPerMissingField() throws Exception {
    // Ni réglages, ni adresse dans identity, ni prestation.
    UUID draft = freeDraft(alice, theo, 5000);
    issue(alice, draft, expected(year, 1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("BILLING_SETTINGS_INCOMPLETE"))
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.errors", hasSize(3)))
        .andExpect(jsonPath("$.errors[0].field").value("siret"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
        .andExpect(jsonPath("$.errors[1].field").value("addressLines"))
        .andExpect(jsonPath("$.errors[2].field").value("service"));
    // Même calcul que missing des réglages.
    perform(MockMvcRequestBuilders.get("/api/me/billing-settings"), alice, null)
        .andExpect(jsonPath("$.missing", hasSize(3)));
    assertThat(storedStatus(draft)).isEqualTo("draft");

    service(alice, "Séance", 6000, true);
    issue(alice, draft, expected(year, 1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors", hasSize(2)));
    assertThat(counter(alice, year)).isZero();
  }

  @Test
  void anEmptyOrNonPositiveInvoiceAnswers422EmptyInvoice() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID empty = draft(alice, "{\"patientId\":\"" + theo + "\"}");
    issue(alice, empty, expected(year, 1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("EMPTY_INVOICE"))
        .andExpect(jsonPath("$.detail").isNotEmpty());
    UUID zero =
        draft(
            alice,
            "{\"patientId\":\""
                + theo
                + "\",\"lines\":[{\"label\":\"Séance\",\"unitPrice\":5000},"
                + "{\"label\":\"Remise\",\"unitPrice\":-5000}]}");
    issue(alice, zero, expected(year, 1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("EMPTY_INVOICE"));
    assertThat(storedStatus(empty)).isEqualTo("draft");
    assertThat(counter(alice, year)).isZero();
  }

  @Test
  void aSessionLineWithoutPositivePriceAnswers422RateRequired() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID session = row(alice, theo, today.minusDays(1), "pending");
    UUID draft =
        draft(
            alice,
            "{\"patientId\":\""
                + theo
                + "\",\"lines\":[{\"sessionEntryId\":\""
                + session
                + "\",\"label\":\"Séance offerte\",\"unitPrice\":0},"
                + "{\"label\":\"Atelier\",\"unitPrice\":3000}]}");
    issue(alice, draft, expected(year, 1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("RATE_REQUIRED"))
        .andExpect(jsonPath("$.sessionEntryId").value(session.toString()));
  }

  @Test
  void alreadyPaidRecordsThePaymentInTheSameTransaction() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID draft = freeDraft(alice, theo, 6000);
    issue(
            alice,
            draft,
            "{\"expectedNumber\":\""
                + number(year, 1)
                + "\",\"alreadyPaid\":{\"mode\":\"cheque\",\"receivedOn\":\""
                + today.minusDays(1)
                + "\",\"reference\":\"Chèque 0012\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("paid"))
        .andExpect(jsonPath("$.paid").value(6000))
        .andExpect(jsonPath("$.balanceDue").value(0))
        .andExpect(jsonPath("$.lastPaymentMode").value("cheque"))
        .andExpect(jsonPath("$.payments", hasSize(1)))
        .andExpect(jsonPath("$.payments[0].invoiceId").value(draft.toString()))
        .andExpect(jsonPath("$.payments[0].invoiceNumber").value(number(year, 1)))
        .andExpect(jsonPath("$.payments[0].kind").value("payment"))
        .andExpect(jsonPath("$.payments[0].mode").value("cheque"))
        .andExpect(jsonPath("$.payments[0].amount").value(6000))
        .andExpect(jsonPath("$.payments[0].receivedOn").value(today.minusDays(1).toString()))
        .andExpect(jsonPath("$.payments[0].reference").value("Chèque 0012"))
        .andExpect(jsonPath("$.payments[0].cancelledAt").value(nullValue()))
        .andExpect(jsonPath("$.history[1].kind").value("issued"))
        .andExpect(jsonPath("$.history[2].kind").value("payment_recorded"))
        .andExpect(jsonPath("$.history[2].detail.mode").value("cheque"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payment where invoice_id = ? and amount_cents = 6000"
                    + " and kind = 'payment' and practitioner_id = ?",
                Integer.class,
                draft,
                alice))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("select paid_cents from invoice where id = ?", Long.class, draft))
        .isEqualTo(6000L);
    // other est toujours accepté, même absent des réglages.
    UUID other = freeDraft(alice, theo, 1000);
    issue(
            alice,
            other,
            "{\"expectedNumber\":\""
                + number(year, 2)
                + "\",\"alreadyPaid\":{\"mode\":\"other\",\"receivedOn\":\""
                + today
                + "\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments[0].mode").value("other"));
  }

  @Test
  void alreadyPaidValidatesItsModeAndDateAndNothingIsIssued() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID draft = freeDraft(alice, theo, 6000);
    // card n'est pas parmi les modes acceptés par défaut (virement, chèque, espèces).
    expectFieldError(
        issue(alice, draft, paid(number(year, 1), "card", today)),
        "alreadyPaid.mode",
        "MODE_NOT_ACCEPTED");
    expectFieldError(
        issue(alice, draft, paid(number(year, 1), "cash", today.plusDays(1))),
        "alreadyPaid.receivedOn",
        "OUT_OF_RANGE");
    expectFieldError(
        issue(alice, draft, paid(number(year, 1), "bitcoin", today)),
        "alreadyPaid.mode",
        "INVALID_VALUE");
    expectFieldError(
        issue(
            alice,
            draft,
            "{\"expectedNumber\":\"" + number(year, 1) + "\",\"alreadyPaid\":{\"mode\":\"cash\"}}"),
        "alreadyPaid.receivedOn",
        "REQUIRED");
    expectFieldError(
        issue(
            alice,
            draft,
            "{\"expectedNumber\":\""
                + number(year, 1)
                + "\",\"alreadyPaid\":{\"mode\":\"cash\",\"receivedOn\":\"hier\"}}"),
        "alreadyPaid.receivedOn",
        "INVALID_FORMAT");
    expectFieldError(
        issue(
            alice,
            draft,
            "{\"expectedNumber\":\""
                + number(year, 1)
                + "\",\"alreadyPaid\":{\"mode\":\"cash\",\"receivedOn\":\""
                + today
                + "\",\"amount\":6000}}"),
        "alreadyPaid.amount",
        "UNKNOWN_FIELD");
    assertThat(storedStatus(draft)).isEqualTo("draft");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payment where invoice_id = ?", Integer.class, draft))
        .isZero();
  }

  @Test
  void issueValidatesItsBodyAndFindsOnlyTheInvoicesOfThePractitioner() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID draft = freeDraft(alice, theo, 6000);
    expectFieldError(issue(alice, draft, "{}"), "expectedNumber", "REQUIRED");
    expectFieldError(
        issue(alice, draft, "{\"expectedNumber\":\"" + "F".repeat(31) + "\"}"),
        "expectedNumber",
        "OUT_OF_RANGE");
    expectFieldError(
        issue(alice, draft, "{\"expectedNumber\":\"F-1\",\"number\":\"F-1\"}"),
        "number",
        "UNKNOWN_FIELD");
    issue(bob, draft, expected(year, 1))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    issue(alice, UUID.randomUUID(), expected(year, 1))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    perform(MockMvcRequestBuilders.post(INVOICES + "/pas-un-uuid/issue"), alice, expected(year, 1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    assertThat(storedStatus(draft)).isEqualTo("draft");
  }

  @Test
  void patchAndDeleteOfAnIssuedInvoiceAnswer409AndNoRouteChangesIt() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID session = row(alice, theo, today.minusDays(2), "pending");
    UUID invoice =
        draft(alice, "{\"patientId\":\"" + theo + "\",\"sessionEntryIds\":[\"" + session + "\"]}");
    issue(alice, invoice, expected(year, 1)).andExpect(status().isOk());
    String before =
        jdbc.queryForObject(
            "select row_to_json(i)::text from invoice i where id = ?", String.class, invoice);

    patch(alice, invoice, "{\"footerNote\":\"Modifiée\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVOICE_ISSUED"));
    patch(alice, invoice, "{\"lines\":[]}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVOICE_ISSUED"));
    delete(alice, invoice)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVOICE_ISSUED"));
    issue(alice, invoice, expected(year, 2))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVOICE_ISSUED"));
    perform(
            MockMvcRequestBuilders.patch("/api/billing/billable-sessions/" + session),
            alice,
            "{\"amount\":9000}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ALREADY_INVOICED"));
    perform(
            MockMvcRequestBuilders.post("/api/billing/billable-sessions/" + session + "/dismiss"),
            alice,
            null)
        .andExpect(status().isConflict());

    assertThat(
            jdbc.queryForObject(
                "select row_to_json(i)::text from invoice i where id = ?", String.class, invoice))
        .isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from invoice_line where invoice_id = ?", Integer.class, invoice))
        .isEqualTo(1);
    assertThat(state(session)).isEqualTo("invoiced");
    assertThat(counter(alice, year)).isEqualTo(1);
  }

  @Test
  void aLiableRegimeFreezesHtVatAndTtc() throws Exception {
    settings(alice);
    jdbc.update(
        "update billing_settings set vat_regime = 'liable', vat_rate_bp = 2000,"
            + " vat_number = 'FR40303265045' where practitioner_id = ?",
        alice);
    service(alice, "Coaching", 10000, true);
    UUID draft = freeDraft(alice, theo, 10000);
    issue(alice, draft, expected(year, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subtotal").value(10000))
        .andExpect(jsonPath("$.vat").value(2000))
        .andExpect(jsonPath("$.total").value(12000))
        .andExpect(jsonPath("$.issuer.vatMention").value(nullValue()))
        .andExpect(jsonPath("$.issuer.vatRateBp").value(2000))
        .andExpect(jsonPath("$.issuer.vatNumber").value("FR40303265045"));
    // Le régime change ensuite : la facture émise garde ses montants et son instantané.
    jdbc.update(
        "update billing_settings set vat_regime = 'franchise', vat_rate_bp = null,"
            + " vat_number = null where practitioner_id = ?",
        alice);
    get(alice, draft)
        .andExpect(jsonPath("$.vat").value(2000))
        .andExpect(jsonPath("$.total").value(12000))
        .andExpect(jsonPath("$.issuer.vatRateBp").value(2000));
    String text =
        pdfText(pdf(alice, draft, null).andReturn().getResponse().getContentAsByteArray());
    assertThat(text).contains("Total HT", "TVA 20 %", "Total TTC", "120,00 €", "FR40303265045");
    assertThat(text).doesNotContain(FRANCHISE);
  }

  // ---- GET /api/invoices/by-number/{number}

  @Test
  void byNumberAnswersLikeByIdAndOnlyForThePractitioner() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID draft = freeDraft(alice, theo, 6000);
    issue(alice, draft, expected(year, 1)).andExpect(status().isOk());

    String byId =
        get(alice, draft).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    String byNumber =
        perform(MockMvcRequestBuilders.get(INVOICES + "/by-number/" + number(year, 1)), alice, null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(byNumber).isEqualTo(byId);

    perform(MockMvcRequestBuilders.get(INVOICES + "/by-number/" + number(year, 1)), bob, null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    perform(MockMvcRequestBuilders.get(INVOICES + "/by-number/F-1999-0001"), alice, null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
  }

  // ---- GET /api/invoices/{id}/pdf

  @Test
  void thePdfCarriesTheNumberTheTotalTheVatMentionAndNoOtherRecordData() throws Exception {
    settings(alice);
    service(alice, "Séance individuelle", 6000, true);
    UUID session = closedSession(alice, theo, "Zéphyrin confidentiel : angoisses du dimanche");
    awaitState(session, "pending");
    LocalDate sessionDay = today;
    UUID draft =
        draft(
            alice,
            "{\"patientId\":\""
                + theo
                + "\",\"sessionEntryIds\":[\""
                + session
                + "\"],\"lines\":[{\"label\":\"Atelier <b>&</b> bilan\",\"unitPrice\":6000}]}");
    issue(alice, draft, expected(year, 1)).andExpect(status().isOk());

    MvcResult result =
        pdf(alice, draft, null)
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"" + number(year, 1) + ".pdf\""))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn();
    String text = pdfText(result.getResponse().getContentAsByteArray());
    assertThat(text)
        .contains(number(year, 1))
        .contains("120,00 €")
        .contains(HEALTH_EXEMPT)
        .contains("Séance individuelle du " + LINE_DATE.format(sessionDay))
        // Texte saisi échappé dans le HTML, rendu tel quel.
        .contains("Atelier <b>&</b> bilan")
        .contains("Théo Bernard")
        .contains("Dr Facturation")
        .contains("732 829 320 00074");
    // Aucune autre donnée du dossier : ni compte rendu, ni nom affiché du patient.
    assertThat(text)
        .doesNotContain("Zéphyrin")
        .doesNotContain("angoisses")
        .doesNotContain("Théo B.");
  }

  @Test
  void theStoredPdfDoesNotChangeWhenTheSettingsChangeAfterwards() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID draft = freeDraft(alice, theo, 6000);
    issue(alice, draft, expected(year, 1)).andExpect(status().isOk());
    byte[] first = pdf(alice, draft, null).andReturn().getResponse().getContentAsByteArray();
    assertThat(first).isEqualTo(storage.get("billing/invoice/" + draft + ".pdf"));

    jdbc.update(
        "update billing_settings set address_lines = '99 avenue Nouvelle', vat_regime ="
            + " 'franchise', display_phone = '09 99 99 99 99', footer_note = 'Nouvelle mention'"
            + " where practitioner_id = ?",
        alice);
    jdbc.update("update practitioner set display_name = 'Dr Renommée' where id = ?", alice);

    byte[] again = pdf(alice, draft, null).andReturn().getResponse().getContentAsByteArray();
    assertThat(again).isEqualTo(first);
    String text = pdfText(again);
    assertThat(text).contains("12 rue des Lilas", HEALTH_EXEMPT, "Dr Facturation");
    assertThat(text).doesNotContain("99 avenue Nouvelle").doesNotContain("Dr Renommée");
  }

  @Test
  void aDraftPdfIsRenderedOnDemandWithAWatermarkAndANeutralName() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID draft = freeDraft(alice, theo, 4500);
    MvcResult result =
        pdf(alice, draft, null)
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Disposition", "attachment; filename=\"facture-brouillon.pdf\""))
            .andReturn();
    String text = pdfText(result.getResponse().getContentAsByteArray());
    assertThat(text).contains("N° attribué à l’émission", "45,00 €", HEALTH_EXEMPT);
    // Filigrane en biais : PDFBox peut couper le mot extrait.
    assertThat(text.replace(" ", "")).contains("Brouillon");
    // Jamais rangé.
    assertThat(storage.exists("billing/invoice/" + draft + ".pdf")).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select pdf_storage_key is null from invoice where id = ?", Boolean.class, draft))
        .isTrue();
    // Rendu avec les réglages du moment.
    jdbc.update(
        "update billing_settings set vat_regime = 'franchise' where practitioner_id = ?", alice);
    assertThat(pdfText(pdf(alice, draft, null).andReturn().getResponse().getContentAsByteArray()))
        .contains(FRANCHISE);
  }

  @Test
  void theReceiptedVariantNeedsAFullyPaidInvoice() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID due = freeDraft(alice, theo, 6000);
    pdf(alice, due, "receipted")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_PAID"));
    issue(alice, due, expected(year, 1)).andExpect(status().isOk());
    pdf(alice, due, "receipted")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_PAID"))
        .andExpect(jsonPath("$.detail").isNotEmpty());

    UUID paid = freeDraft(alice, theo, 6000);
    issue(alice, paid, paid(number(year, 2), "transfer", today)).andExpect(status().isOk());
    MvcResult result =
        pdf(alice, paid, "receipted")
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"" + number(year, 2) + "-acquittee.pdf\""))
            .andReturn();
    String text = pdfText(result.getResponse().getContentAsByteArray()).toLowerCase(Locale.ROOT);
    assertThat(text)
        .contains("facture acquittée")
        .contains("acquittée")
        .contains("réglé par virement le " + LINE_DATE.format(today))
        .contains(number(year, 2).toLowerCase(Locale.ROOT));
    // Le fichier rangé, lui, reste la facture émise.
    assertThat(pdf(alice, paid, null).andReturn().getResponse().getContentAsByteArray())
        .isEqualTo(storage.get("billing/invoice/" + paid + ".pdf"));
  }

  @Test
  void thePdfRouteValidatesTheVariantAndFindsOnlyThePractitionersInvoices() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID draft = freeDraft(alice, theo, 6000);
    expectFieldError(pdf(alice, draft, "duplicata"), "variant", "INVALID_VALUE");
    pdf(bob, draft, null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    perform(MockMvcRequestBuilders.get(INVOICES + "/pas-un-uuid/pdf"), alice, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  // ---- POST /api/invoices/{id}/duplicate

  @Test
  void duplicateCreatesADraftWithTheSameLinesWithoutTheirSessions() throws Exception {
    settings(alice);
    UUID service = service(alice, "Séance individuelle", 6000, true);
    UUID session = row(alice, theo, today.minusDays(4), "pending");
    UUID source =
        draft(
            alice,
            "{\"patientId\":\""
                + theo
                + "\",\"sessionEntryIds\":[\""
                + session
                + "\"],\"lines\":[{\"label\":\"Remise\",\"quantity\":2,\"unitPrice\":-500}]}");
    patch(alice, source, "{\"recipient\":{\"name\":\"Mutuelle Alpha\"}}")
        .andExpect(status().isOk());
    issue(alice, source, expected(year, 1)).andExpect(status().isOk());

    MvcResult result =
        perform(MockMvcRequestBuilders.post(INVOICES + "/" + source + "/duplicate"), alice, null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("draft"))
            .andExpect(jsonPath("$.number").value(nullValue()))
            .andExpect(jsonPath("$.patient.id").value(theo.toString()))
            .andExpect(jsonPath("$.issueDate").value(today.toString()))
            .andExpect(jsonPath("$.recipient.name").value("Théo Bernard"))
            .andExpect(jsonPath("$.lines", hasSize(2)))
            .andExpect(
                jsonPath("$.lines[0].label")
                    .value("Séance individuelle du " + LINE_DATE.format(today.minusDays(4))))
            .andExpect(jsonPath("$.lines[0].occurredOn").value(today.minusDays(4).toString()))
            .andExpect(jsonPath("$.lines[0].serviceId").value(service.toString()))
            .andExpect(jsonPath("$.lines[0].sessionEntryId").value(nullValue()))
            .andExpect(jsonPath("$.lines[0].unitPrice").value(6000))
            .andExpect(jsonPath("$.lines[1].quantity").value(2))
            .andExpect(jsonPath("$.lines[1].amount").value(-1000))
            .andExpect(jsonPath("$.total").value(5000))
            .andExpect(jsonPath("$.subject.sessionCount").value(0))
            .andExpect(jsonPath("$.nextNumberPreview").value(number(year, 2)))
            .andExpect(jsonPath("$.history", hasSize(1)))
            .andExpect(jsonPath("$.history[0].kind").value("draft_created"))
            .andExpect(jsonPath("$.history[0].detail.sessionCount").value(0))
            .andExpect(jsonPath("$.history[0].detail.duplicateOf").value(source.toString()))
            .andReturn();
    UUID copy = id(result);
    assertThat(copy).isNotEqualTo(source);
    // La séance reste dans la facture d'origine ; aucune ligne copiée ne porte de séance.
    assertThat(invoiceOf(session)).isEqualTo(source);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from invoice_line where invoice_id = ? and"
                    + " (session_entry_id is not null or source_line_id is not null)",
                Integer.class,
                copy))
        .isZero();
    assertThat(storedStatus(source)).isEqualTo("issued");
  }

  @Test
  void duplicateRefusesAnArchivedPatientACreditNoteAndAnotherPractitioner() throws Exception {
    settings(alice);
    service(alice, "Séance", 6000, true);
    UUID draft = freeDraft(alice, theo, 6000);
    perform(MockMvcRequestBuilders.post(INVOICES + "/" + draft + "/duplicate"), bob, null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));

    issue(alice, draft, expected(year, 1)).andExpect(status().isOk());
    UUID creditNote = creditNoteInSql(alice, theo, "AV-" + year + "-0001", draft);
    perform(MockMvcRequestBuilders.post(INVOICES + "/" + creditNote + "/duplicate"), alice, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CREDIT_NOTE_NOT_DUPLICABLE"));

    jdbc.update("update patient set archived_at = now() where id = ?", theo);
    perform(MockMvcRequestBuilders.post(INVOICES + "/" + draft + "/duplicate"), alice, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"))
        .andExpect(jsonPath("$.patientId").value(theo.toString()));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from invoice where practitioner_id = ?", Integer.class, alice))
        .isEqualTo(2);
  }

  // ---- outils : chemin réel de clinical

  private UUID closedSession(UUID who, UUID patient, String summary) throws Exception {
    MvcResult started =
        perform(
                MockMvcRequestBuilders.post("/api/patients/" + patient + "/sessions/start"),
                who,
                "{\"modality\":\"in_person\"}")
            .andExpect(status().isCreated())
            .andReturn();
    String location = started.getResponse().getHeader("Location");
    UUID session = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    perform(
            MockMvcRequestBuilders.post("/api/sessions/" + session + "/close"),
            who,
            "{\"durationMin\":50,\"summary\":\"" + summary + "\"}")
        .andExpect(status().isOk());
    return session;
  }

  private void awaitState(UUID session, String expected) {
    await()
        .atMost(WAIT)
        .until(
            () ->
                expected.equals(
                    jdbc
                        .queryForList(
                            "select state from billable_session where session_entry_id = ?",
                            String.class,
                            session)
                        .stream()
                        .findFirst()
                        .orElse(null)));
  }

  // ---- outils : PDF

  private static String pdfText(byte[] pdf) throws Exception {
    assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      // Espaces insécables des montants ramenés à des espaces ordinaires.
      return new PDFTextStripper().getText(doc).replace(' ', ' ').replaceAll("\\s+", " ");
    }
  }

  // ---- outils : base

  private UUID practitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Facturation', 'Dr Facturation',"
            + " 'psychologist')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  private UUID patient(UUID owner, String displayName, String firstName, String lastName) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name, first_name, last_name)"
            + " values (?, ?, ?, ?, ?, ?)",
        id,
        tenant,
        owner,
        displayName,
        firstName,
        lastName);
    return id;
  }

  /** Réglages complets : SIRET, adresse, e-mail, téléphone, ordre des chèques. */
  private void settings(UUID owner) {
    jdbc.update(
        "insert into billing_settings (practitioner_id, tenant_id, siret, address_lines,"
            + " display_email, display_phone, cheque_payee) values (?, ?, ?, ?, ?,"
            + " '01 00 00 00 00', 'Dr Facturation')",
        owner,
        tenant,
        SIRET,
        "12 rue des Lilas\n75011 Paris",
        "cabinet@example.test");
  }

  private UUID service(UUID owner, String label, int price, boolean isDefault) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into billing_service (id, tenant_id, practitioner_id, label, price_cents,"
            + " is_default) values (?, ?, ?, ?, ?, ?)",
        id,
        tenant,
        owner,
        label,
        price,
        isDefault);
    return id;
  }

  /** Ligne billable_session écrite directement (séance fictive, date maîtrisée). */
  private UUID row(UUID owner, UUID patient, LocalDate occurredOn, String state) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into billable_session (session_entry_id, tenant_id, practitioner_id, patient_id,"
            + " occurred_on, modality, duration_min, state) values (?, ?, ?, ?, ?, 'in_person', 50,"
            + " ?)",
        id,
        tenant,
        owner,
        patient,
        Date.valueOf(occurredOn),
        state);
    return id;
  }

  /** Facture émise écrite en SQL (une année passée), conforme aux contraintes de V18. */
  private void issuedInSql(UUID owner, UUID patient, String number, LocalDate issueDate) {
    jdbc.update(
        "insert into invoice (id, tenant_id, practitioner_id, patient_id, kind, status, number,"
            + " recipient_name, issue_date, due_term, issuer_snapshot, subtotal_cents, total_cents,"
            + " issued_at) values (?, ?, ?, ?, 'invoice', 'issued', ?, 'Destinataire', ?,"
            + " 'on_receipt', cast('{\"name\":\"Dr\"}' as jsonb), 6000, 6000, now())",
        UUID.randomUUID(),
        tenant,
        owner,
        patient,
        number,
        Date.valueOf(issueDate));
  }

  /** Avoir (toujours émis) annulant une facture, écrit en SQL (M15-11 n'existe pas encore). */
  private UUID creditNoteInSql(UUID owner, UUID patient, String number, UUID cancels) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into invoice (id, tenant_id, practitioner_id, patient_id, kind, status, number,"
            + " cancels_invoice_id, issue_date, due_term, issuer_snapshot, subtotal_cents,"
            + " total_cents, issued_at) values (?, ?, ?, ?, 'credit_note', 'issued', ?, ?, ?,"
            + " 'on_receipt', cast('{\"name\":\"Dr\"}' as jsonb), 6000, 6000, now())",
        id,
        tenant,
        owner,
        patient,
        number,
        cancels,
        Date.valueOf(today));
    return id;
  }

  private int counter(UUID owner, int year) {
    return jdbc
        .queryForList(
            "select last_number from billing_counter where practitioner_id = ? and kind ="
                + " 'invoice' and year = ?",
            Integer.class,
            owner,
            year)
        .stream()
        .findFirst()
        .orElse(0);
  }

  private String storedStatus(UUID invoice) {
    return jdbc.queryForObject("select status from invoice where id = ?", String.class, invoice);
  }

  private List<String> eventKinds(UUID invoice) {
    return jdbc.queryForList(
        "select kind from invoice_event where invoice_id = ? order by at, id",
        String.class,
        invoice);
  }

  private String state(UUID session) {
    return jdbc.queryForObject(
        "select state from billable_session where session_entry_id = ?", String.class, session);
  }

  private UUID invoiceOf(UUID session) {
    return jdbc.queryForObject(
        "select invoice_id from billable_session where session_entry_id = ?", UUID.class, session);
  }

  // ---- outils : corps

  private static String number(int year, int n) {
    return String.format(Locale.ROOT, "F-%d-%04d", year, n);
  }

  private static String expected(int year, int n) {
    return "{\"expectedNumber\":\"" + number(year, n) + "\"}";
  }

  private static String paid(String expectedNumber, String mode, LocalDate receivedOn) {
    return "{\"expectedNumber\":\""
        + expectedNumber
        + "\",\"alreadyPaid\":{\"mode\":\""
        + mode
        + "\",\"receivedOn\":\""
        + receivedOn
        + "\"}}";
  }

  // ---- outils : HTTP

  private UUID draft(UUID who, String body) throws Exception {
    return id(
        perform(MockMvcRequestBuilders.post(INVOICES), who, body)
            .andExpect(status().isCreated())
            .andReturn());
  }

  /** Brouillon d'une ligne libre au prix donné. */
  private UUID freeDraft(UUID who, UUID patient, int cents) throws Exception {
    return draft(
        who,
        "{\"patientId\":\""
            + patient
            + "\",\"lines\":[{\"label\":\"Séance de suivi\",\"unitPrice\":"
            + cents
            + "}]}");
  }

  private static UUID id(MvcResult result) throws Exception {
    return UUID.fromString(
        JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id"));
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
            "Dr Facturation",
            "psychologist",
            "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions perform(MockHttpServletRequestBuilder builder, UUID who, String body)
      throws Exception {
    if (body != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.getBytes(StandardCharsets.UTF_8));
    }
    return mvc.perform(builder.with(as(who)));
  }

  private ResultActions issue(UUID who, UUID invoice, String body) throws Exception {
    return perform(MockMvcRequestBuilders.post(INVOICES + "/" + invoice + "/issue"), who, body);
  }

  private ResultActions pdf(UUID who, UUID invoice, String variant) throws Exception {
    MockHttpServletRequestBuilder builder =
        MockMvcRequestBuilders.get(INVOICES + "/" + invoice + "/pdf");
    if (variant != null) {
      builder.param("variant", variant);
    }
    return perform(builder, who, null);
  }

  private ResultActions list(UUID who, String query) throws Exception {
    return perform(MockMvcRequestBuilders.get(INVOICES + query), who, null);
  }

  private ResultActions get(UUID who, UUID invoice) throws Exception {
    return perform(MockMvcRequestBuilders.get(INVOICES + "/" + invoice), who, null);
  }

  private ResultActions patch(UUID who, UUID invoice, String body) throws Exception {
    return perform(MockMvcRequestBuilders.patch(INVOICES + "/" + invoice), who, body);
  }

  private ResultActions delete(UUID who, UUID invoice) throws Exception {
    return perform(MockMvcRequestBuilders.delete(INVOICES + "/" + invoice), who, null);
  }
}
