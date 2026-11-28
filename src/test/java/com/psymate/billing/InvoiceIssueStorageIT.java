package com.psymate.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.EncryptedStorageFacade;
import com.psymate.testsupport.SharedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Rangement du PDF qui échoue à l'émission (M15-09, G Q93) : le stockage chiffré réel est espionné
 * (la base, elle, est réelle) ; son écriture d'un PDF de facture écrit le fichier puis échoue,
 * comme un disque plein au milieu d'une écriture. L'émission répond 500 PDF_UNAVAILABLE et rien
 * n'est émis : la facture reste brouillon, le compteur ne bouge pas, aucun fichier ne reste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class InvoiceIssueStorageIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;
  @MockitoSpyBean private EncryptedStorageFacade storage;

  private UUID tenant;
  private UUID alice;
  private UUID theo;
  private int year;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet stockage')", tenant);
    alice = UUID.randomUUID();
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Stockage', 'Dr Stockage', 'psychologist')",
        alice,
        tenant,
        alice + "@example.test",
        alice + "@example.test");
    theo = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'Théo B.')",
        theo,
        tenant,
        alice);
    jdbc.update(
        "insert into billing_settings (practitioner_id, tenant_id, siret, address_lines)"
            + " values (?, ?, '73282932000074', '1 rue de la Paix')",
        alice,
        tenant);
    jdbc.update(
        "insert into billing_service (id, tenant_id, practitioner_id, label, price_cents,"
            + " is_default) values (?, ?, ?, 'Séance', 6000, true)",
        UUID.randomUUID(),
        tenant,
        alice);
    year = LocalDate.now(clock.withZone(ZoneId.of("Europe/Paris"))).getYear();
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    reset(storage);
  }

  @Test
  void aStorageFailureAtIssueAnswersPdfUnavailableAndIssuesNothing() throws Exception {
    UUID draft = draft();
    String key = "billing/invoice/" + draft + ".pdf";
    doAnswer(
            call -> {
              call.callRealMethod();
              throw new UncheckedIOException(new IOException("écriture interrompue"));
            })
        .when(storage)
        .put(startsWith("billing/invoice/"), any());

    mvc.perform(
            MockMvcRequestBuilders.post("/api/invoices/" + draft + "/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("{\"expectedNumber\":\""
                            + String.format(Locale.ROOT, "F-%d-0001", year)
                            + "\"}")
                        .getBytes(StandardCharsets.UTF_8))
                .with(as(alice)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("PDF_UNAVAILABLE"))
        .andExpect(jsonPath("$.detail").isNotEmpty());

    assertThat(
            jdbc.queryForObject(
                "select status || '|' || (number is null) || '|' || (pdf_storage_key is null)"
                    + " from invoice where id = ?",
                String.class,
                draft))
        .isEqualTo("draft|true|true");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from billing_counter where practitioner_id = ?"
                    + " and last_number > 0",
                Integer.class,
                alice))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from invoice_event where invoice_id = ? and kind = 'issued'",
                Integer.class,
                draft))
        .isZero();
    // Le fichier écrit avant l'échec est effacé à l'annulation de la transaction.
    assertThat(storage.exists(key)).isFalse();
  }

  private UUID draft() throws Exception {
    MvcResult result =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        ("{\"patientId\":\""
                                + theo
                                + "\",\"lines\":[{\"label\":\"Séance\",\"unitPrice\":6000}]}")
                            .getBytes(StandardCharsets.UTF_8))
                    .with(as(alice)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(
        JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id"));
  }

  private RequestPostProcessor as(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            tenant,
            practitionerId + "@example.test",
            "Dr Stockage",
            "psychologist",
            "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }
}
