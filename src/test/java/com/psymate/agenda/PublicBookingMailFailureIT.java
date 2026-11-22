package com.psymate.agenda;

import static com.psymate.agenda.PublicBookingFixtures.applicantEmail;
import static com.psymate.agenda.PublicBookingFixtures.body;
import static com.psymate.agenda.PublicBookingFixtures.request;
import static com.psymate.agenda.PublicBookingFixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.agenda.PublicBookingFixtures.Practitioner;
import com.psymate.testsupport.SharedPostgres;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Échec du serveur d'envoi (M14-06, G Q81) : SMTP injoignable, aucune réponse HTTP ne change. Pas
 * de base simulée ; seul le SMTP est remplacé par un serveur réel mais injoignable, comme pour le
 * formulaire de contact.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, PublicBookingMailFailureIT.UnreachableSmtp.class})
class PublicBookingMailFailureIT {

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

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void smtpFailureChangesNoHttpResponse() throws Exception {
    PublicBookingFixtures f = new PublicBookingFixtures(mvc, jdbc);
    Practitioner p = f.practitioner("manual");

    MvcResult created =
        f.postRequest(p.slug(), request(slot(3, "09:00"), applicantEmail()))
            .andExpect(status().isAccepted())
            .andReturn();
    assertThat(body(created)).isEmpty();
    assertThat(f.requestsOf(p.id())).isEqualTo(1);

    String token = f.pendingRequest(p, slot(3, "10:00"), applicantEmail());
    String manageToken =
        PublicBookingFixtures.json(
                f.verify(token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("pending"))
                    .andReturn())
            .path("manageToken")
            .asString();
    assertThat(manageToken).hasSize(43);
    // Laisse partir (et échouer) les envois de la vérification avant l'annulation.
    Thread.sleep(500);

    f.cancel(manageToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"));
    f.manage(manageToken).andExpect(status().isOk());
  }
}
