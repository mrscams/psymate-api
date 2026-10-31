package com.psymate.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Échec du relais e-mail (SMTP injoignable) : le visiteur a quand même son 202, la ligne existe,
 * relayed_at reste NULL et relay_error est renseigné. Pas de mock de la base : seul le SMTP est
 * remplacé par un serveur réel mais injoignable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({DirectoryTestConfig.class, ContactRelayFailureIT.UnreachableSmtp.class})
class ContactRelayFailureIT {

  @TestConfiguration(proxyBeanMethods = false)
  static class UnreachableSmtp {
    @Bean
    @Primary
    JavaMailSender unreachableMailSender() {
      JavaMailSenderImpl sender = new JavaMailSenderImpl();
      sender.setHost("127.0.0.1");
      // Port 1 (tcpmux) : jamais ouvert en local, la connexion est refusée immédiatement.
      sender.setPort(1);
      Properties props = new Properties();
      props.setProperty("mail.smtp.connectiontimeout", "2000");
      props.setProperty("mail.smtp.timeout", "2000");
      props.setProperty("mail.smtp.writetimeout", "2000");
      sender.setJavaMailProperties(props);
      return sender;
    }
  }

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void smtpFailureIsRecordedInRelayErrorAndNeverReachesTheVisitor() throws Exception {
    UUID tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    UUID id = UUID.randomUUID();
    String tag = id.toString().substring(0, 8);
    String account = "compte-" + tag + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'T', 'P', ?, 'psychologist', 'approved')",
        id,
        tenant,
        account,
        account,
        "Praticien " + tag);
    String slug = "relay-fail-" + tag;
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, display_name, published,"
            + " contact_email) values (?, ?, ?, true, ?)",
        id,
        slug,
        "Praticien " + tag,
        "cabinet-" + tag + "@example.test");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Camille Visiteur");
    body.put("email", "camille@example.test");
    body.put("subject", "Première consultation");
    body.put("message", "Bonjour, je souhaite prendre rendez-vous.");
    body.put("consent", true);
    body.put("website", "");
    body.put("formShownAt", Instant.now().minusSeconds(30).toString());

    mvc.perform(
            post("/api/public/directory/practitioners/" + slug + "/contact")
                .with(
                    r -> {
                      r.setRemoteAddr("10.9.9.9");
                      return r;
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)))
        .andExpect(status().isAccepted());

    Map<String, Object> row = awaitRelayOutcome(id);
    assertThat(row.get("relayed_at")).isNull();
    assertThat((String) row.get("relay_error")).isNotBlank();
    assertThat(((String) row.get("relay_error")).length())
        .isLessThanOrEqualTo(ContactRequestService.MAX_RELAY_ERROR);
  }

  private Map<String, Object> awaitRelayOutcome(UUID practitionerId) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (true) {
      Map<String, Object> row =
          jdbc.queryForMap(
              "select * from contact_request where practitioner_id = ?", practitionerId);
      if (row.get("relayed_at") != null || row.get("relay_error") != null) {
        return row;
      }
      assertThat(System.nanoTime()).as("relais non terminé").isLessThan(deadline);
      Thread.sleep(100);
    }
  }
}
