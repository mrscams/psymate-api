package com.psymate.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Brouillons de facture et liste (M15-08, L5.3 jusqu'à DELETE ; G Q86, Q87, Q88, Q92) sur une vraie
 * base (Testcontainers pgvector/pgvector:pg16) : chaque route et chaque code, calculs de L3.2 en
 * centimes, lot tout ou rien, ordre et filtres de la liste (chaque statut), séances passées à
 * invoiced puis rendues à pending, isolement entre praticiens.
 *
 * <p>Les séances sont des lignes {@code billable_session} écrites en SQL (table du module, dates
 * maîtrisées) ; les factures émises, annulées et les avoirs, qu'aucune route n'émet encore (M15-09,
 * M15-11), sont écrites en SQL conformes aux contraintes de V18. Le jour de Paris est lu sur
 * l'horloge du contexte, celle du serveur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class InvoiceIT {

  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
  private static final String INVOICES = "/api/invoices";
  private static final DateTimeFormatter LINE_DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);
  private static final String SNAPSHOT =
      "{\"name\":\"Dr Instantané\",\"title\":\"Psychologue\",\"siret\":\"73282932000074\","
          + "\"addressLines\":\"1 rue de la Paix\",\"vatRegime\":\"health_exempt\","
          + "\"vatMention\":\"TVA non applicable, article 261-4-1° du Code général des impôts\","
          + "\"acceptedModes\":[\"cash\"],\"bankDetailsShown\":false}";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private UUID tenant;
  private UUID alice;
  private UUID bob;
  private UUID theo;
  private UUID lea;
  private UUID bobsPatient;
  private LocalDate today;
  private LocalDate day1;
  private LocalDate day2;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet factures')", tenant);
    alice = practitioner();
    bob = practitioner();
    theo = patient(alice, "Théo B.", "Théo", "Bernard");
    lea = patient(alice, "Léa M.", null, null);
    bobsPatient = patient(bob, "Patient de Bob", null, null);
    today = today();
    day1 = today.minusDays(20);
    day2 = today.minusDays(10);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- POST /api/invoices

  @Test
  void createsADraftWithOneLinePerSessionPricedByQ87() throws Exception {
    UUID service = service(alice, "Séance individuelle", 6000, true);
    UUID fixed = row(alice, theo, day2, "pending");
    jdbc.update(
        "update billable_session set amount_cents = 7000 where session_entry_id = ?", fixed);
    UUID byService = row(alice, theo, day1, "pending");

    MvcResult result =
        create(
                alice,
                "{\"patientId\":\""
                    + theo
                    + "\",\"sessionEntryIds\":[\""
                    + fixed
                    + "\",\""
                    + byService
                    + "\"],\"lines\":[{\"label\":\"Remise fidélité\",\"unitPrice\":-500}]}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("invoice"))
            .andExpect(jsonPath("$.status").value("draft"))
            .andExpect(jsonPath("$.number").value(nullValue()))
            .andExpect(jsonPath("$.overdue").value(false))
            .andExpect(jsonPath("$.patient.id").value(theo.toString()))
            .andExpect(jsonPath("$.patient.displayName").value("Théo B."))
            .andExpect(jsonPath("$.issueDate").value(today.toString()))
            .andExpect(jsonPath("$.dueTerm").value("on_receipt"))
            .andExpect(jsonPath("$.dueOn").value(today.toString()))
            // Lignes de séance par date, puis la ligne libre.
            .andExpect(jsonPath("$.lines", hasSize(3)))
            .andExpect(jsonPath("$.lines[0].position").value(1))
            .andExpect(jsonPath("$.lines[0].sessionEntryId").value(byService.toString()))
            .andExpect(jsonPath("$.lines[0].occurredOn").value(day1.toString()))
            .andExpect(
                jsonPath("$.lines[0].label")
                    .value("Séance individuelle du " + LINE_DATE.format(day1)))
            .andExpect(jsonPath("$.lines[0].serviceId").value(service.toString()))
            .andExpect(jsonPath("$.lines[0].quantity").value(1))
            .andExpect(jsonPath("$.lines[0].unitPrice").value(6000))
            .andExpect(jsonPath("$.lines[0].amount").value(6000))
            .andExpect(jsonPath("$.lines[0].credited").value(false))
            .andExpect(jsonPath("$.lines[1].sessionEntryId").value(fixed.toString()))
            .andExpect(jsonPath("$.lines[1].unitPrice").value(7000))
            .andExpect(jsonPath("$.lines[2].sessionEntryId").value(nullValue()))
            .andExpect(jsonPath("$.lines[2].label").value("Remise fidélité"))
            .andExpect(jsonPath("$.lines[2].amount").value(-500))
            .andExpect(jsonPath("$.subtotal").value(12_500))
            .andExpect(jsonPath("$.vat").value(0))
            .andExpect(jsonPath("$.total").value(12_500))
            .andExpect(jsonPath("$.balanceDue").value(12_500))
            .andExpect(jsonPath("$.paid").value(0))
            .andExpect(jsonPath("$.credited").value(0))
            .andExpect(jsonPath("$.refundDue").value(0))
            .andExpect(jsonPath("$.subject.sessionCount").value(2))
            .andExpect(jsonPath("$.subject.firstOn").value(day1.toString()))
            .andExpect(jsonPath("$.subject.lastOn").value(day2.toString()))
            .andExpect(jsonPath("$.subject.label").value(nullValue()))
            .andExpect(jsonPath("$.nextNumberPreview").value("F-" + today.getYear() + "-0001"))
            .andExpect(jsonPath("$.recipient.name").value("Théo Bernard"))
            .andExpect(jsonPath("$.recipient.patientName").value(nullValue()))
            .andExpect(jsonPath("$.payments", hasSize(0)))
            .andExpect(jsonPath("$.creditNotes", hasSize(0)))
            .andExpect(jsonPath("$.cancels").value(nullValue()))
            .andExpect(jsonPath("$.history", hasSize(1)))
            .andExpect(jsonPath("$.history[0].kind").value("draft_created"))
            .andExpect(jsonPath("$.history[0].detail.sessionCount").value(2))
            .andExpect(jsonPath("$.history[0].detail.batch").value(false))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();
    UUID invoice = id(result);
    // Séances passées à invoiced dans la même transaction, rattachées au brouillon.
    assertThat(state(fixed)).isEqualTo("invoiced");
    assertThat(state(byService)).isEqualTo("invoiced");
    assertThat(invoiceOf(byService)).isEqualTo(invoice);
    // Montants rangés en centimes ; aucune ligne de séance ne porte de texte clinique.
    assertThat(
            jdbc.queryForObject(
                "select total_cents from invoice where id = ?", Long.class, invoice))
        .isEqualTo(12_500L);
    // La séance quitte la liste des séances à facturer ; le brouillon y est compté.
    perform(MockMvcRequestBuilders.get("/api/billing/billable-sessions"), alice, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups", hasSize(0)));
    UUID third = row(alice, theo, day2, "pending");
    perform(MockMvcRequestBuilders.get("/api/billing/billable-sessions"), alice, null)
        .andExpect(jsonPath("$.groups[0].sessions[0].sessionEntryId").value(third.toString()))
        .andExpect(jsonPath("$.groups[0].openDraftCount").value(1));
  }

  @Test
  void aSessionWithoutServiceIsNamedSeanceDuAndPricedByThePatientRate() throws Exception {
    rate(theo, 5500);
    UUID session = row(alice, theo, day1, "pending");

    create(alice, body(theo, session))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].label").value("Séance du " + LINE_DATE.format(day1)))
        .andExpect(jsonPath("$.lines[0].serviceId").value(nullValue()))
        .andExpect(jsonPath("$.lines[0].unitPrice").value(5500))
        .andExpect(jsonPath("$.total").value(5500));
  }

  @Test
  void anEmptyDraftCanBeCreatedAndItsSubjectIsItsFirstLine() throws Exception {
    create(
            alice,
            "{\"patientId\":\""
                + lea
                + "\",\"lines\":[{\"label\":\"Bilan\",\"unitPrice\":15000,\"quantity\":2,"
                + "\"occurredOn\":\""
                + day1
                + "\"}]}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].quantity").value(2))
        .andExpect(jsonPath("$.lines[0].occurredOn").value(day1.toString()))
        .andExpect(jsonPath("$.total").value(30_000))
        .andExpect(jsonPath("$.subject.sessionCount").value(0))
        .andExpect(jsonPath("$.subject.label").value("Bilan"))
        // Sans prénom ni nom, le destinataire est le nom affiché.
        .andExpect(jsonPath("$.recipient.name").value("Léa M."));
    create(alice, "{\"patientId\":\"" + lea + "\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines", hasSize(0)))
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void aThirdPartyPayerIsTheDefaultRecipient() throws Exception {
    jdbc.update(
        "insert into billing_patient (patient_id, tenant_id, practitioner_id, address_lines,"
            + " third_party_name, third_party_address) values (?, ?, ?, '1 rue A',"
            + " 'Mutuelle X', '2 avenue B')",
        theo,
        tenant,
        alice);

    create(alice, "{\"patientId\":\"" + theo + "\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recipient.name").value("Mutuelle X"))
        .andExpect(jsonPath("$.recipient.addressLines").value("2 avenue B"))
        .andExpect(jsonPath("$.recipient.patientName").value("Théo Bernard"));
  }

  @Test
  void theIssuerOfADraftReflectsTheSettingsOfTheMomentAndItsTotalFollowsL32() throws Exception {
    service(alice, "Séance", 1234, true);
    UUID session = row(alice, theo, day1, "pending");
    UUID invoice = id(create(alice, body(theo, session)).andReturn());
    get(alice, invoice)
        .andExpect(jsonPath("$.issuer.name").value("Dr Facturation"))
        .andExpect(jsonPath("$.issuer.title").value("Psychologue"))
        .andExpect(
            jsonPath("$.issuer.vatMention")
                .value("TVA non applicable, article 261-4-1° du Code général des impôts"))
        .andExpect(jsonPath("$.issuer.vatRateBp").value(nullValue()))
        .andExpect(jsonPath("$.vat").value(0))
        .andExpect(jsonPath("$.total").value(1234));

    // Régime assujetti à 5,5 % ensuite : 1 234 × 5,5 % = 67,87 → 68 (arrondi demi supérieur).
    jdbc.update(
        "insert into billing_settings (practitioner_id, tenant_id, siret, vat_regime, vat_rate_bp,"
            + " vat_number, invoice_prefix) values (?, ?, '73282932000074', 'liable', 550,"
            + " 'FR40303265045', 'FAC')",
        alice,
        tenant);
    get(alice, invoice)
        .andExpect(jsonPath("$.issuer.siret").value("73282932000074"))
        .andExpect(jsonPath("$.issuer.vatMention").value(nullValue()))
        .andExpect(jsonPath("$.issuer.vatRateBp").value(550))
        .andExpect(jsonPath("$.issuer.vatNumber").value("FR40303265045"))
        .andExpect(jsonPath("$.subtotal").value(1234))
        .andExpect(jsonPath("$.vat").value(68))
        .andExpect(jsonPath("$.total").value(1302))
        .andExpect(jsonPath("$.balanceDue").value(1302))
        .andExpect(jsonPath("$.nextNumberPreview").value("FAC-" + today.getYear() + "-0001"));
    // La liste calcule le même total en SQL.
    list(alice, "").andExpect(jsonPath("$.items[0].total").value(1302));
  }

  @Test
  void theNumberPreviewFollowsTheCounterOfTheIssueYear() throws Exception {
    UUID invoice = id(create(alice, "{\"patientId\":\"" + lea + "\"}").andReturn());
    jdbc.update(
        "insert into billing_counter (practitioner_id, kind, year, last_number)"
            + " values (?, 'invoice', ?, 41)",
        alice,
        today.getYear());
    get(alice, invoice)
        .andExpect(jsonPath("$.nextNumberPreview").value("F-" + today.getYear() + "-0042"));
  }

  @Test
  void createRefusesAnArchivedPatient() throws Exception {
    rate(theo, 5500);
    UUID session = row(alice, theo, day1, "pending");
    jdbc.update("update patient set archived_at = now() where id = ?", theo);

    create(alice, body(theo, session))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"))
        .andExpect(jsonPath("$.patientId").value(theo.toString()))
        .andExpect(jsonPath("$.detail").isNotEmpty());
    assertThat(state(session)).isEqualTo("pending");
    assertThat(invoiceCount(alice)).isZero();
  }

  @Test
  void createRefusesASessionAlreadyInvoicedAndNamesIt() throws Exception {
    rate(theo, 5500);
    UUID session = row(alice, theo, day1, "pending");
    UUID other = row(alice, theo, day2, "pending");
    UUID first = id(create(alice, body(theo, session)).andReturn());

    create(alice, body(theo, other, session))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ALREADY_INVOICED"))
        .andExpect(jsonPath("$.sessionEntryId").value(session.toString()))
        .andExpect(jsonPath("$.detail").isNotEmpty());
    // Rien n'est écrit : l'autre séance reste à facturer, un seul brouillon.
    assertThat(state(other)).isEqualTo("pending");
    assertThat(invoiceOf(session)).isEqualTo(first);
    assertThat(invoiceCount(alice)).isEqualTo(1);
  }

  @Test
  void createRefusesASessionWithoutRate() throws Exception {
    UUID session = row(alice, theo, day1, "pending");

    create(alice, body(theo, session))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("RATE_REQUIRED"))
        .andExpect(jsonPath("$.sessionEntryId").value(session.toString()))
        .andExpect(jsonPath("$.detail").isNotEmpty());
    assertThat(state(session)).isEqualTo("pending");
    assertThat(invoiceCount(alice)).isZero();
  }

  @Test
  void createRefusesAnUnknownSessionAndASessionOfAnotherPatient() throws Exception {
    rate(theo, 5500);
    rate(lea, 5500);
    create(alice, body(theo, UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_BILLABLE"));

    UUID leasSession = row(alice, lea, day1, "pending");
    expectFieldError(create(alice, body(theo, leasSession)), "sessionEntryIds[0]", "INVALID_VALUE");
    assertThat(state(leasSession)).isEqualTo("pending");
  }

  @Test
  void createRefusesThePatientOfAnotherPractitioner() throws Exception {
    create(alice, "{\"patientId\":\"" + bobsPatient + "\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    create(alice, "{\"patientId\":\"" + UUID.randomUUID() + "\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
  }

  @Test
  void createValidatesItsBody() throws Exception {
    expectFieldError(create(alice, "{}"), "patientId", "REQUIRED");
    expectFieldError(create(alice, "{\"patientId\":\"x\"}"), "patientId", "INVALID_FORMAT");
    expectFieldError(
        create(alice, "{\"patientId\":\"" + lea + "\",\"total\":1}"), "total", "UNKNOWN_FIELD");
    expectFieldError(
        create(
            alice,
            "{\"patientId\":\"" + lea + "\",\"lines\":[{\"label\":\"A\",\"unitPrice\":60.5}]}"),
        "lines[0].unitPrice",
        "INVALID_FORMAT");
    expectFieldError(
        create(
            alice,
            "{\"patientId\":\"" + lea + "\",\"lines\":[{\"label\":\"A\",\"unitPrice\":\"6000\"}]}"),
        "lines[0].unitPrice",
        "INVALID_FORMAT");
    expectFieldError(
        create(alice, "{\"patientId\":\"" + lea + "\",\"lines\":[{\"unitPrice\":6000}]}"),
        "lines[0].label",
        "REQUIRED");
    expectFieldError(
        create(
            alice,
            "{\"patientId\":\""
                + lea
                + "\",\"lines\":[{\"label\":\"A\",\"unitPrice\":1,\"quantity\":1000}]}"),
        "lines[0].quantity",
        "OUT_OF_RANGE");
    expectFieldError(
        create(
            alice,
            "{\"patientId\":\""
                + lea
                + "\",\"lines\":[{\"label\":\"A\",\"unitPrice\":1,\"serviceId\":\""
                + UUID.randomUUID()
                + "\"}]}"),
        "lines[0].serviceId",
        "INVALID_VALUE");
    UUID session = UUID.randomUUID();
    expectFieldError(
        create(
            alice,
            "{\"patientId\":\""
                + lea
                + "\",\"sessionEntryIds\":[\""
                + session
                + "\",\""
                + session
                + "\"]}"),
        "sessionEntryIds[1]",
        "INVALID_VALUE");
    assertThat(invoiceCount(alice)).isZero();
  }

  @Test
  void aDraftHasAtMostOneHundredFreeLines() throws Exception {
    String hundred = freeLines(100);
    create(alice, "{\"patientId\":\"" + lea + "\",\"lines\":[" + hundred + "]}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines", hasSize(100)))
        .andExpect(jsonPath("$.total").value(100));
    expectFieldError(
        create(alice, "{\"patientId\":\"" + lea + "\",\"lines\":[" + freeLines(101) + "]}"),
        "lines",
        "OUT_OF_RANGE");
  }

  // ---- POST /api/invoices/batch

  @Test
  void batchCreatesOneDraftPerPatient() throws Exception {
    service(alice, "Séance", 6000, true);
    UUID theo1 = row(alice, theo, day2, "pending");
    UUID lea1 = row(alice, lea, day1, "pending");
    UUID theo2 = row(alice, theo, day1, "pending");

    MvcResult result =
        batch(alice, ids(theo1, lea1, theo2))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.invoices", hasSize(2)))
            .andExpect(jsonPath("$.invoices[0].patient.id").value(theo.toString()))
            .andExpect(jsonPath("$.invoices[0].patient.displayName").value("Théo B."))
            .andExpect(jsonPath("$.invoices[0].total").value(12_000))
            .andExpect(jsonPath("$.invoices[1].patient.id").value(lea.toString()))
            .andExpect(jsonPath("$.invoices[1].total").value(6000))
            .andReturn();
    UUID theosDraft =
        UUID.fromString(
            JsonPath.read(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.invoices[0].id"));
    assertThat(invoiceOf(theo1)).isEqualTo(theosDraft);
    assertThat(invoiceOf(theo2)).isEqualTo(theosDraft);
    assertThat(state(lea1)).isEqualTo("invoiced");
    get(alice, theosDraft)
        .andExpect(jsonPath("$.lines[0].sessionEntryId").value(theo2.toString()))
        .andExpect(jsonPath("$.lines[1].sessionEntryId").value(theo1.toString()))
        .andExpect(jsonPath("$.history[0].detail.batch").value(true));
  }

  @Test
  void batchIsAllOrNothing() throws Exception {
    service(alice, "Séance", 6000, true);
    UUID theo1 = row(alice, theo, day1, "pending");
    UUID lea1 = row(alice, lea, day1, "pending");
    UUID bobs = row(bob, bobsPatient, day1, "pending");

    // Une séance inconnue du praticien (celle de Bob) : rien n'est créé.
    batch(alice, ids(theo1, lea1, bobs))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_BILLABLE"));
    // Un patient archivé : rien n'est créé.
    jdbc.update("update patient set archived_at = now() where id = ?", lea);
    batch(alice, ids(theo1, lea1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"))
        .andExpect(jsonPath("$.patientId").value(lea.toString()));
    jdbc.update("update patient set archived_at = null where id = ?", lea);
    // Une séance sans tarif (plus de prestation, Léa sans tarif) : celle de Théo ne part pas seule.
    jdbc.update(
        "update billing_service set archived_at = now(), is_default = false"
            + " where practitioner_id = ?",
        alice);
    rate(theo, 5500);
    batch(alice, ids(theo1, lea1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("RATE_REQUIRED"))
        .andExpect(jsonPath("$.sessionEntryId").value(lea1.toString()));
    // Une séance déjà facturée : rien n'est créé.
    rate(lea, 5000);
    UUID leaDraft = id(create(alice, body(lea, lea1)).andReturn());
    batch(alice, ids(theo1, lea1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ALREADY_INVOICED"))
        .andExpect(jsonPath("$.sessionEntryId").value(lea1.toString()));

    assertThat(state(theo1)).isEqualTo("pending");
    assertThat(invoiceOf(lea1)).isEqualTo(leaDraft);
    assertThat(invoiceCount(alice)).isEqualTo(1);
    assertThat(state(bobs)).isEqualTo("pending");
  }

  @Test
  void batchValidatesItsBody() throws Exception {
    expectFieldError(batch(alice, "{\"sessionEntryIds\":[]}"), "sessionEntryIds", "REQUIRED");
    expectFieldError(batch(alice, "{}"), "sessionEntryIds", "REQUIRED");
    String tooMany =
        IntStream.range(0, 201)
            .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
            .collect(Collectors.joining(","));
    expectFieldError(
        batch(alice, "{\"sessionEntryIds\":[" + tooMany + "]}"), "sessionEntryIds", "OUT_OF_RANGE");
    UUID session = UUID.randomUUID();
    expectFieldError(batch(alice, ids(session, session)), "sessionEntryIds[1]", "INVALID_VALUE");
    expectFieldError(
        batch(alice, "{\"sessionEntryIds\":[1]}"), "sessionEntryIds", "INVALID_FORMAT");
  }

  // ---- GET /api/invoices

  @Test
  void theListPutsDraftsFirstThenIssueDateDescendingThenId() throws Exception {
    UUID old = issued(alice, theo, "F-2026-0001", today.minusDays(30), 6000, 0, 0, "issued");
    UUID recent = issued(alice, lea, "F-2026-0003", today.minusDays(5), 6000, 0, 0, "issued");
    UUID middle = issued(alice, theo, "F-2026-0002", today.minusDays(15), 6000, 0, 0, "issued");
    UUID olderDraft = id(create(alice, "{\"patientId\":\"" + theo + "\"}").andReturn());
    patch(alice, olderDraft, "{\"issueDate\":\"" + today.minusDays(40) + "\"}")
        .andExpect(status().isOk());
    UUID draft = id(create(alice, "{\"patientId\":\"" + lea + "\"}").andReturn());

    list(alice, "")
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.items[*].id")
                .value(
                    contains(
                        draft.toString(),
                        olderDraft.toString(),
                        recent.toString(),
                        middle.toString(),
                        old.toString())))
        .andExpect(jsonPath("$.items[0].status").value("draft"))
        .andExpect(jsonPath("$.items[0].number").value(nullValue()))
        .andExpect(jsonPath("$.items[2].number").value("F-2026-0003"))
        .andExpect(jsonPath("$.items[2].patient.displayName").value("Léa M."))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()));

    // Même date : l'identifiant départage, dans l'ordre de PostgreSQL.
    UUID sameDay = issued(alice, theo, "F-2026-0004", today.minusDays(5), 6000, 0, 0, "issued");
    List<String> expected =
        jdbc.queryForList(
            "select id::text from invoice where id in (?, ?) order by id",
            String.class,
            recent,
            sameDay);
    list(alice, "?status=due")
        .andExpect(
            jsonPath("$.items[*].id")
                .value(
                    contains(expected.get(0), expected.get(1), middle.toString(), old.toString())));
  }

  @Test
  void theListPagesWithAnOpaqueCursor() throws Exception {
    List<String> all = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      all.add(
          issued(alice, theo, "F-2026-01" + i, today.minusDays(i + 1), 6000, 0, 0, "issued")
              .toString());
    }
    List<String> seen = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      MvcResult page =
          list(alice, "?limit=2" + (cursor == null ? "" : "&cursor=" + cursor))
              .andExpect(status().isOk())
              .andReturn();
      String json = page.getResponse().getContentAsString(StandardCharsets.UTF_8);
      List<String> ids = JsonPath.read(json, "$.items[*].id");
      seen.addAll(ids);
      cursor = JsonPath.read(json, "$.nextCursor");
      pages++;
    } while (cursor != null && pages < 10);
    assertThat(pages).isEqualTo(3);
    assertThat(seen).containsExactlyElementsOf(all);
  }

  @Test
  void theListFiltersByEachStatusKindAndOverdue() throws Exception {
    UUID draft = id(create(alice, "{\"patientId\":\"" + theo + "\"}").andReturn());
    UUID due = issued(alice, theo, "F-2026-0001", today.minusDays(3), 6000, 0, 0, "issued");
    UUID partial = issued(alice, theo, "F-2026-0002", today.minusDays(4), 6000, 1000, 0, "issued");
    UUID paid = issued(alice, theo, "F-2026-0003", today.minusDays(5), 6000, 6000, 0, "issued");
    UUID credited =
        issued(alice, theo, "F-2026-0004", today.minusDays(6), 6000, 1000, 5000, "issued");
    UUID voided = issued(alice, lea, "F-2026-0005", today.minusDays(7), 6000, 0, 6000, "void");
    UUID creditNote = creditNote(alice, lea, "AV-2026-0001", today.minusDays(7), voided);
    // Échéances à venir pour toutes, sauf une échéance passée, reste dû positif : en retard
    // depuis 5 jours.
    jdbc.update(
        "update invoice set due_term = 'date', due_on = ? where practitioner_id = ?"
            + " and status <> 'draft'",
        Date.valueOf(today.plusDays(10)),
        alice);
    UUID overdue = issued(alice, lea, "F-2026-0006", today.minusDays(40), 8000, 0, 0, "issued");
    jdbc.update(
        "update invoice set due_term = 'days_30', due_on = ? where id = ?",
        Date.valueOf(today.minusDays(5)),
        overdue);

    expectIds(list(alice, "?status=draft"), draft);
    expectIds(list(alice, "?status=due"), due, overdue);
    expectIds(list(alice, "?status=partial"), partial);
    expectIds(list(alice, "?status=paid"), paid, credited);
    expectIds(list(alice, "?status=void"), voided);
    expectIds(list(alice, "?kind=credit_note"), creditNote);
    expectIds(list(alice, "?overdue=true"), overdue);
    expectIds(list(alice, "?patientId=" + lea + "&status=due"), overdue);
    expectIds(list(alice, "?from=" + today.minusDays(4)), draft, due, partial);

    list(alice, "?overdue=true")
        .andExpect(jsonPath("$.items[0].overdue").value(true))
        .andExpect(jsonPath("$.items[0].overdueDays").value(oneOf(5, 6)))
        .andExpect(jsonPath("$.items[0].dueOn").value(today.minusDays(5).toString()))
        .andExpect(jsonPath("$.items[0].balanceDue").value(8000));
    list(alice, "?status=partial")
        .andExpect(jsonPath("$.items[0].balanceDue").value(5000))
        .andExpect(jsonPath("$.items[0].overdue").value(false))
        .andExpect(jsonPath("$.items[0].overdueDays").value(nullValue()));
    list(alice, "?kind=credit_note")
        .andExpect(jsonPath("$.items[0].status").value("issued"))
        .andExpect(jsonPath("$.items[0].balanceDue").value(0))
        .andExpect(jsonPath("$.items[0].cancelsNumber").value("F-2026-0005"));
    list(alice, "?status=void")
        .andExpect(jsonPath("$.items[0].creditNoteNumber").value("AV-2026-0001"))
        .andExpect(jsonPath("$.items[0].balanceDue").value(0));
  }

  @Test
  void theListSearchesTheNumberThePatientNameAndTheRecipient() throws Exception {
    UUID helene = patient(alice, "Hélène R.", "Hélène", "Roux");
    UUID byNumber = issued(alice, theo, "F-2026-0042", today.minusDays(2), 6000, 0, 0, "issued");
    UUID byName = issued(alice, helene, "F-2026-0043", today.minusDays(3), 6000, 0, 0, "issued");
    UUID byRecipient = id(create(alice, "{\"patientId\":\"" + lea + "\"}").andReturn());
    patch(alice, byRecipient, "{\"recipient\":{\"name\":\"Mutuelle Générale\"}}")
        .andExpect(status().isOk());

    expectIds(list(alice, "?q=0042"), byNumber);
    expectIds(list(alice, "?q=HELENE"), byName);
    expectIds(list(alice, "?q=generale"), byRecipient);
    expectIds(list(alice, "?q=%25%25"));
    expectFieldError(list(alice, "?q=a"), "q", "OUT_OF_RANGE");
  }

  @Test
  void theListValidatesItsParameters() throws Exception {
    expectFieldError(list(alice, "?status=issued"), "status", "INVALID_VALUE");
    expectFieldError(list(alice, "?kind=quote"), "kind", "INVALID_VALUE");
    expectFieldError(list(alice, "?cursor=abc"), "cursor", "INVALID_FORMAT");
    expectFieldError(list(alice, "?from=28-11-2026"), "from", "INVALID_FORMAT");
    expectFieldError(list(alice, "?overdue=maybe"), "overdue", "INVALID_FORMAT");
    expectFieldError(list(alice, "?limit=0"), "limit", "OUT_OF_RANGE");
    expectFieldError(list(alice, "?limit=101"), "limit", "OUT_OF_RANGE");
    list(alice, "?patientId=" + bobsPatient)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
  }

  // ---- GET /api/invoices/{id}

  @Test
  void anIssuedInvoiceShowsItsSnapshotAndFrozenAmounts() throws Exception {
    UUID invoice = issued(alice, theo, "F-2026-0007", today.minusDays(2), 6000, 0, 0, "issued");
    // Réglages changés après l'émission : le document émis garde son instantané.
    jdbc.update(
        "insert into billing_settings (practitioner_id, tenant_id, vat_regime, vat_rate_bp,"
            + " vat_number) values (?, ?, 'liable', 2000, 'FR40303265045')",
        alice,
        tenant);

    get(alice, invoice)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("due"))
        .andExpect(jsonPath("$.number").value("F-2026-0007"))
        .andExpect(jsonPath("$.issuer.name").value("Dr Instantané"))
        .andExpect(jsonPath("$.issuer.siret").value("73282932000074"))
        .andExpect(jsonPath("$.issuer.acceptedModes", contains("cash")))
        .andExpect(jsonPath("$.issuer.vatRateBp").value(nullValue()))
        .andExpect(jsonPath("$.total").value(6000))
        .andExpect(jsonPath("$.vat").value(0))
        .andExpect(jsonPath("$.nextNumberPreview").value(nullValue()));
  }

  @Test
  void getAnswers404ForAnUnknownInvoiceAnd400ForAMalformedId() throws Exception {
    get(alice, UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").isNotEmpty());
    perform(MockMvcRequestBuilders.get(INVOICES + "/pas-un-uuid"), alice, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  // ---- PATCH /api/invoices/{id}

  @Test
  void patchMergesTheDraftFields() throws Exception {
    UUID invoice = id(create(alice, "{\"patientId\":\"" + theo + "\"}").andReturn());
    LocalDate issueOn = today.plusDays(2);
    LocalDate dueOn = today.plusDays(20);

    patch(
            alice,
            invoice,
            "{\"recipient\":{\"name\":\"  Mme Roux \",\"addressLines\":\"3 place C\"},"
                + "\"issueDate\":\""
                + issueOn
                + "\",\"dueTerm\":\"date\",\"dueOn\":\""
                + dueOn
                + "\",\"footerNote\":\"Merci.\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recipient.name").value("Mme Roux"))
        .andExpect(jsonPath("$.recipient.addressLines").value("3 place C"))
        .andExpect(jsonPath("$.recipient.patientName").value("Théo Bernard"))
        .andExpect(jsonPath("$.issueDate").value(issueOn.toString()))
        .andExpect(jsonPath("$.dueTerm").value("date"))
        .andExpect(jsonPath("$.dueOn").value(dueOn.toString()))
        .andExpect(jsonPath("$.footerNote").value("Merci."));

    // Échéance à 15 jours : la date choisie s'efface ; null efface la mention ; null rend le
    // destinataire par défaut.
    patch(alice, invoice, "{\"dueTerm\":\"days_15\",\"footerNote\":null,\"recipient\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dueTerm").value("days_15"))
        .andExpect(jsonPath("$.dueOn").value(issueOn.plusDays(15).toString()))
        .andExpect(jsonPath("$.footerNote").value(nullValue()))
        .andExpect(jsonPath("$.recipient.name").value("Théo Bernard"))
        .andExpect(jsonPath("$.recipient.patientName").value(nullValue()));
    assertThat(
            jdbc.queryForObject(
                "select due_on is null from invoice where id = ?", Boolean.class, invoice))
        .isTrue();
  }

  @Test
  void patchValidatesTheDraftFields() throws Exception {
    UUID invoice = id(create(alice, "{\"patientId\":\"" + theo + "\"}").andReturn());
    expectFieldError(patch(alice, invoice, "{\"dueTerm\":\"date\"}"), "dueOn", "REQUIRED");
    expectFieldError(
        patch(alice, invoice, "{\"dueOn\":\"" + today.plusDays(3) + "\"}"),
        "dueOn",
        "INVALID_VALUE");
    expectFieldError(
        patch(alice, invoice, "{\"dueTerm\":\"date\",\"dueOn\":\"" + today.minusDays(1) + "\"}"),
        "dueOn",
        "OUT_OF_RANGE");
    expectFieldError(
        patch(alice, invoice, "{\"dueTerm\":\"days_60\"}"), "dueTerm", "INVALID_VALUE");
    expectFieldError(patch(alice, invoice, "{\"issueDate\":null}"), "issueDate", "REQUIRED");
    expectFieldError(
        patch(alice, invoice, "{\"issueDate\":\"hier\"}"), "issueDate", "INVALID_FORMAT");
    expectFieldError(patch(alice, invoice, "{\"lines\":null}"), "lines", "REQUIRED");
    expectFieldError(patch(alice, invoice, "{\"patientId\":null}"), "patientId", "REQUIRED");
    expectFieldError(
        patch(alice, invoice, "{\"recipient\":{\"name\":\" \"}}"), "recipient.name", "REQUIRED");
    expectFieldError(patch(alice, invoice, "{\"number\":\"F-1\"}"), "number", "UNKNOWN_FIELD");
  }

  @Test
  void removingASessionLineReturnsTheSessionToPending() throws Exception {
    service(alice, "Séance", 6000, true);
    UUID kept = row(alice, theo, day1, "pending");
    UUID removed = row(alice, theo, day2, "pending");
    UUID added = row(alice, theo, day2, "pending");
    UUID invoice = id(create(alice, body(theo, kept, removed)).andReturn());

    patch(
            alice,
            invoice,
            "{\"lines\":[{\"sessionEntryId\":\""
                + kept
                + "\",\"label\":\"Séance longue\",\"unitPrice\":8000,\"occurredOn\":\"2000-01-01\"},"
                + "{\"sessionEntryId\":\""
                + added
                + "\",\"label\":\"Séance\",\"unitPrice\":6000},"
                + "{\"label\":\"Remise\",\"unitPrice\":-1000}]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines", hasSize(3)))
        .andExpect(jsonPath("$.lines[0].sessionEntryId").value(kept.toString()))
        .andExpect(jsonPath("$.lines[0].label").value("Séance longue"))
        .andExpect(jsonPath("$.lines[0].occurredOn").value(day1.toString()))
        .andExpect(jsonPath("$.lines[0].unitPrice").value(8000))
        .andExpect(jsonPath("$.lines[1].sessionEntryId").value(added.toString()))
        .andExpect(jsonPath("$.lines[2].position").value(3))
        .andExpect(jsonPath("$.subtotal").value(13_000))
        .andExpect(jsonPath("$.total").value(13_000))
        .andExpect(jsonPath("$.subject.sessionCount").value(2));

    assertThat(state(removed)).isEqualTo("pending");
    assertThat(invoiceOf(removed)).isNull();
    assertThat(invoiceOf(kept)).isEqualTo(invoice);
    assertThat(invoiceOf(added)).isEqualTo(invoice);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from invoice_line where invoice_id = ?", Integer.class, invoice))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "select subtotal_cents from invoice where id = ?", Long.class, invoice))
        .isEqualTo(13_000L);

    // Toutes les lignes retirées : toutes les séances reviennent à facturer.
    patch(alice, invoice, "{\"lines\":[]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines", hasSize(0)))
        .andExpect(jsonPath("$.total").value(0));
    assertThat(state(kept)).isEqualTo("pending");
    assertThat(state(added)).isEqualTo("pending");
  }

  @Test
  void patchLinesRefusesASessionOfAnotherDraftOrPatient() throws Exception {
    rate(theo, 5000);
    rate(lea, 5000);
    UUID theirs = row(alice, theo, day1, "pending");
    id(create(alice, body(theo, theirs)).andReturn());
    UUID invoice = id(create(alice, "{\"patientId\":\"" + theo + "\"}").andReturn());
    UUID leas = row(alice, lea, day1, "pending");

    patch(alice, invoice, "{\"lines\":[" + sessionLine(theirs) + "]}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ALREADY_INVOICED"))
        .andExpect(jsonPath("$.sessionEntryId").value(theirs.toString()));
    expectFieldError(
        patch(alice, invoice, "{\"lines\":[" + sessionLine(leas) + "]}"),
        "lines[0].sessionEntryId",
        "INVALID_VALUE");
    expectFieldError(
        patch(alice, invoice, "{\"lines\":[" + sessionLine(leas) + "," + sessionLine(leas) + "]}"),
        "lines[1].sessionEntryId",
        "INVALID_VALUE");
    assertThat(state(leas)).isEqualTo("pending");
  }

  @Test
  void thePatientChangesOnlyWithoutSessionLines() throws Exception {
    rate(theo, 5000);
    UUID session = row(alice, theo, day1, "pending");
    UUID withSession = id(create(alice, body(theo, session)).andReturn());
    expectFieldError(
        patch(alice, withSession, "{\"patientId\":\"" + lea + "\"}"), "patientId", "INVALID_VALUE");

    UUID empty = id(create(alice, "{\"patientId\":\"" + theo + "\"}").andReturn());
    patch(alice, empty, "{\"patientId\":\"" + lea + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.patient.id").value(lea.toString()))
        .andExpect(jsonPath("$.recipient.name").value("Léa M."));
    patch(alice, empty, "{\"patientId\":\"" + bobsPatient + "\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    UUID archived = patient(alice, "Archivé A.", null, null);
    jdbc.update("update patient set archived_at = now() where id = ?", archived);
    patch(alice, empty, "{\"patientId\":\"" + archived + "\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"));
  }

  @Test
  void patchAndDeleteOfAnIssuedInvoiceAnswer409() throws Exception {
    UUID invoice = issued(alice, theo, "F-2026-0009", today.minusDays(2), 6000, 0, 0, "issued");
    UUID voided = issued(alice, theo, "F-2026-0010", today.minusDays(2), 6000, 0, 6000, "void");
    UUID creditNote = creditNote(alice, theo, "AV-2026-0002", today.minusDays(1), voided);

    for (UUID id : List.of(invoice, voided, creditNote)) {
      patch(alice, id, "{\"footerNote\":\"x\"}")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INVOICE_ISSUED"))
          .andExpect(jsonPath("$.detail").isNotEmpty());
      delete(alice, id)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INVOICE_ISSUED"));
    }
    assertThat(invoiceCount(alice)).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "select footer_note is null from invoice where id = ?", Boolean.class, invoice))
        .isTrue();
  }

  // ---- DELETE /api/invoices/{id}

  @Test
  void deletingADraftReturnsItsSessionsToPending() throws Exception {
    rate(theo, 5000);
    UUID first = row(alice, theo, day1, "pending");
    UUID second = row(alice, theo, day2, "pending");
    UUID invoice = id(create(alice, body(theo, first, second)).andReturn());

    delete(alice, invoice).andExpect(status().isNoContent());

    assertThat(state(first)).isEqualTo("pending");
    assertThat(state(second)).isEqualTo("pending");
    assertThat(invoiceOf(first)).isNull();
    assertThat(invoiceCount(alice)).isZero();
    // Lignes et historique partis en cascade (E9).
    assertThat(
            jdbc.queryForObject(
                "select count(*) from invoice_line where invoice_id = ?", Integer.class, invoice))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from invoice_event where invoice_id = ?", Integer.class, invoice))
        .isZero();
    delete(alice, invoice)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    // Les séances se facturent de nouveau.
    create(alice, body(theo, first, second)).andExpect(status().isCreated());
  }

  // ---- isolement entre praticiens

  @Test
  void aPractitionerNeverSeesNorTouchesTheInvoicesOfAnother() throws Exception {
    rate(theo, 5000);
    UUID session = row(alice, theo, day1, "pending");
    UUID draft = id(create(alice, body(theo, session)).andReturn());
    UUID issuedOne = issued(alice, theo, "F-2026-0011", today.minusDays(2), 6000, 0, 0, "issued");
    UUID bobs = id(create(bob, "{\"patientId\":\"" + bobsPatient + "\"}").andReturn());

    expectIds(list(bob, ""), bobs);
    expectIds(list(bob, "?q=0011"));
    get(bob, draft).andExpect(status().isNotFound());
    get(bob, issuedOne).andExpect(status().isNotFound());
    patch(bob, draft, "{\"footerNote\":\"x\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    delete(bob, draft).andExpect(status().isNotFound());
    // Bob ne facture pas la séance d'Alice, ni dans un brouillon ni par lot.
    UUID aliceSession = row(alice, theo, day2, "pending");
    patch(bob, bobs, "{\"lines\":[" + sessionLine(aliceSession) + "]}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_BILLABLE"));
    batch(bob, ids(aliceSession))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_BILLABLE"));

    assertThat(invoiceOf(session)).isEqualTo(draft);
    assertThat(state(aliceSession)).isEqualTo("pending");
    expectIds(list(alice, ""), draft, issuedOne);
  }

  // ---- outils : jour de Paris, lu sur l'horloge injectée du serveur

  private LocalDate today() {
    return LocalDate.now(clock.withZone(PARIS));
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

  /** Tarif du patient (profil de facturation), au praticien propriétaire. */
  private void rate(UUID patient, int cents) {
    UUID owner =
        jdbc.queryForObject("select owner_id from patient where id = ?", UUID.class, patient);
    jdbc.update(
        "insert into billing_patient (patient_id, tenant_id, practitioner_id, rate_cents)"
            + " values (?, ?, ?, ?) on conflict (patient_id) do update set rate_cents ="
            + " excluded.rate_cents",
        patient,
        tenant,
        owner,
        cents);
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

  /**
   * Facture émise ou annulée écrite en SQL, conforme aux contraintes de V18 (numéro et instantané
   * présents) ; échéance à réception.
   */
  private UUID issued(
      UUID owner,
      UUID patient,
      String number,
      LocalDate issueDate,
      long total,
      long paid,
      long credited,
      String status) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into invoice (id, tenant_id, practitioner_id, patient_id, kind, status, number,"
            + " recipient_name, issue_date, due_term, issuer_snapshot, subtotal_cents, total_cents,"
            + " paid_cents, credited_cents, issued_at) values (?, ?, ?, ?, 'invoice', ?, ?,"
            + " 'Destinataire', ?, 'on_receipt', cast(? as jsonb), ?, ?, ?, ?, now())",
        id,
        tenant,
        owner,
        patient,
        status,
        number,
        Date.valueOf(issueDate),
        SNAPSHOT,
        total,
        total,
        paid,
        credited);
    return id;
  }

  /** Avoir (toujours émis) annulant une facture, écrit en SQL. */
  private UUID creditNote(
      UUID owner, UUID patient, String number, LocalDate issueDate, UUID cancels) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into invoice (id, tenant_id, practitioner_id, patient_id, kind, status, number,"
            + " cancels_invoice_id, issue_date, due_term, issuer_snapshot, subtotal_cents,"
            + " total_cents, issued_at) values (?, ?, ?, ?, 'credit_note', 'issued', ?, ?, ?,"
            + " 'on_receipt', cast(? as jsonb), 6000, 6000, now())",
        id,
        tenant,
        owner,
        patient,
        number,
        cancels,
        Date.valueOf(issueDate),
        SNAPSHOT);
    return id;
  }

  private String state(UUID session) {
    return jdbc.queryForObject(
        "select state from billable_session where session_entry_id = ?", String.class, session);
  }

  private UUID invoiceOf(UUID session) {
    return jdbc.queryForObject(
        "select invoice_id from billable_session where session_entry_id = ?", UUID.class, session);
  }

  private int invoiceCount(UUID owner) {
    return jdbc.queryForObject(
        "select count(*) from invoice where practitioner_id = ?", Integer.class, owner);
  }

  // ---- outils : corps

  private static String body(UUID patient, UUID... sessions) {
    return "{\"patientId\":\"" + patient + "\"," + ids(sessions).substring(1);
  }

  private static String ids(UUID... sessions) {
    return "{\"sessionEntryIds\":["
        + Arrays.stream(sessions).map(s -> "\"" + s + "\"").collect(Collectors.joining(","))
        + "]}";
  }

  private static String sessionLine(UUID session) {
    return "{\"sessionEntryId\":\"" + session + "\",\"label\":\"Séance\",\"unitPrice\":5000}";
  }

  private static String freeLines(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> "{\"label\":\"Ligne " + i + "\",\"unitPrice\":1}")
        .collect(Collectors.joining(","));
  }

  // ---- outils : HTTP

  private static UUID id(MvcResult result) throws Exception {
    return UUID.fromString(
        JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id"));
  }

  private static void expectIds(ResultActions result, UUID... ids) throws Exception {
    result.andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(ids.length)));
    if (ids.length > 0) {
      result.andExpect(
          jsonPath("$.items[*].id")
              .value(
                  containsInAnyOrder(
                      Arrays.stream(ids).map(UUID::toString).toArray(String[]::new))));
    }
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

  private ResultActions create(UUID who, String body) throws Exception {
    return perform(MockMvcRequestBuilders.post(INVOICES), who, body);
  }

  private ResultActions batch(UUID who, String body) throws Exception {
    return perform(MockMvcRequestBuilders.post(INVOICES + "/batch"), who, body);
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
