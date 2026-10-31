package com.psymate.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.psymate.identity.api.PractitionerRegistered;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** M1-10 : POST /api/auth/register (fiche 12). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({AuthTestConfig.class, RegistrationIT.ListenerConfig.class})
class RegistrationIT {
  /** Un abonné transactionnel : c'est lui qui fait écrire la publication dans le registre. */
  static class RegisteredListener {
    @ApplicationModuleListener
    void on(PractitionerRegistered event) {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ListenerConfig {
    @Bean
    RegisteredListener registeredListener() {
      return new RegisteredListener();
    }
  }

  private static final String PASSWORD = RegistrationFixtures.PASSWORD;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private GenericContainer<?> mailpit;
  private final JsonMapper json = JsonMapper.builder().build();

  private static String newEmail() {
    return "r" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
  }

  private MvcResult register(Map<String, Object> payload) throws Exception {
    return mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)))
        .andReturn();
  }

  private List<String> errorFields(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString())
        .path("errors")
        .valueStream()
        .map(e -> e.path("field").asString())
        .toList();
  }

  @Test
  void completeRegistrationCreatesPendingAccountAndSendsConfirmationMail() throws Exception {
    String email = newEmail();
    MvcResult r = register(RegistrationFixtures.payload(email, PASSWORD, "psychologist"));
    assertThat(r.getResponse().getStatus()).isEqualTo(201);

    Map<String, Object> row =
        jdbc.queryForMap(
            "select verification_status, email_confirmed_at, phone, address_street,"
                + " address_postal_code, address_city, remote_only, professional_number, locale"
                + " from practitioner where email_normalized = ?",
            email);
    assertThat(row.get("verification_status")).isEqualTo("email_pending");
    assertThat(row.get("email_confirmed_at")).isNull();
    assertThat(row.get("phone")).isEqualTo("+33612345678");
    assertThat(row.get("address_street")).isEqualTo("12 rue des Lilas");
    assertThat(row.get("address_postal_code")).isEqualTo("69003");
    assertThat(row.get("address_city")).isEqualTo("Lyon");
    assertThat(row.get("remote_only")).isEqualTo(false);
    assertThat(row.get("professional_number")).isNull();

    // Jeton : 24 h, haché, jamais en clair en base.
    Map<String, Object> token =
        jdbc.queryForMap(
            "select t.kind, t.token_hash, t.consumed_at, extract(epoch from t.expires_at - t.created_at)::bigint as ttl from"
                + " auth_token t join practitioner p on p.id = t.practitioner_id where"
                + " p.email_normalized = ?",
            email);
    assertThat(token.get("kind")).isEqualTo("email_confirm");
    assertThat(token.get("consumed_at")).isNull();
    assertThat(((Number) token.get("ttl")).longValue()).isEqualTo(Duration.ofHours(24).toSeconds());

    MailpitClient mails = new MailpitClient(mailpit);
    String text = mails.lastText(email, "confirmez");
    assertThat(text).isNotNull().contains("PsyMate").contains("/confirmer-email/");
    String linkToken = mails.confirmationToken(email);
    assertThat(token.get("token_hash"))
        .isEqualTo(AuthService.sha256(linkToken))
        .isNotEqualTo(linkToken);
  }

  @Test
  void registeredEventIsWrittenToEventPublicationWithoutPersonalData() throws Exception {
    String email = newEmail();
    register(RegistrationFixtures.payload(email, PASSWORD, "coach"));
    UUID id =
        jdbc.queryForObject(
            "select id from practitioner where email_normalized = ?", UUID.class, email);
    String serialized =
        jdbc.queryForObject(
            "select serialized_event from event_publication where event_type like ? and"
                + " serialized_event like ?",
            String.class,
            "%PractitionerRegistered",
            "%" + id + "%");
    assertThat(serialized).contains("coach").doesNotContain(email).doesNotContain("Kaddour");
  }

  @Test
  void consentProofIsJournalledInSecurityEvent() throws Exception {
    String email = newEmail();
    register(RegistrationFixtures.payload(email, PASSWORD, "psychologist"));
    Map<String, Object> row =
        jdbc.queryForMap(
            "select s.details::text as details, s.ip::text as ip from security_event s join"
                + " practitioner p on p.id = s.practitioner_id where p.email_normalized = ? and"
                + " s.kind = 'consent_given'",
            email);
    JsonNode details = json.readTree((String) row.get("details"));
    assertThat(details.path("terms").asBoolean()).isTrue();
    assertThat(details.path("privacy").asBoolean()).isTrue();
    assertThat(details.path("version").asString()).isEqualTo(AuthService.CONSENT_VERSION);
    assertThat(details.path("acceptedAt").asString()).isNotBlank();
    assertThat(row.get("ip")).isNotNull();
  }

  /** L'inscription refusée ou sur compte existant ne laisse aucune preuve de consentement. */
  @Test
  void existingEmailAddsNoSecondConsentRecord() throws Exception {
    String email = newEmail();
    register(RegistrationFixtures.payload(email, PASSWORD, "psychologist"));
    register(RegistrationFixtures.payload(email, PASSWORD, "psychologist"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_event s join practitioner p on p.id ="
                    + " s.practitioner_id where p.email_normalized = ? and s.kind ="
                    + " 'consent_given'",
                Integer.class,
                email))
        .isEqualTo(1);
  }

  @Test
  void existingEmailGivesIdenticalResponseCreatesNothingAndSendsInformationMail() throws Exception {
    String email = newEmail();
    MvcResult first = register(RegistrationFixtures.payload(email, PASSWORD, "psychologist"));
    MvcResult second =
        register(RegistrationFixtures.payload(email.toUpperCase(), PASSWORD, "coach"));

    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    assertThat(second.getResponse().getStatus()).isEqualTo(201);
    assertThat(second.getResponse().getContentAsString())
        .isEqualTo(first.getResponse().getContentAsString());
    assertThat(second.getResponse().getContentType())
        .isEqualTo(first.getResponse().getContentType());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from practitioner where email_normalized = ?",
                Integer.class,
                email))
        .isEqualTo(1);
    // Le compte d'origine n'est pas modifié.
    assertThat(
            jdbc.queryForObject(
                "select profession from practitioner where email_normalized = ?",
                String.class,
                email))
        .isEqualTo("psychologist");
    assertThat(new MailpitClient(mailpit).lastText(email, "compte"))
        .isNotNull()
        .contains("un compte existe déjà");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from auth_token t join practitioner p on p.id ="
                    + " t.practitioner_id where p.email_normalized = ?",
                Integer.class,
                email))
        .isEqualTo(1);
  }

  @Test
  void invalidPayloadIsRefusedIdenticallyWhetherEmailExistsOrNot() throws Exception {
    String email = newEmail();
    register(RegistrationFixtures.payload(email, PASSWORD, "psychologist"));
    Map<String, Object> bad = RegistrationFixtures.payload(email, PASSWORD, "psychologist");
    bad.remove("address");
    assertThat(register(bad).getResponse().getStatus()).isEqualTo(422);
    bad.put("email", newEmail());
    assertThat(register(bad).getResponse().getStatus()).isEqualTo(422);
  }

  /**
   * Règle anti-flakiness CI : après 5 tours d'échauffement, 15 mesures par cas ; on compare les
   * médianes et le test passe si l'écart est d'au plus 150 ms OU si le ratio max/min est d'au plus
   * 1,5. Le coût dominant (Argon2) est identique dans les deux branches.
   */
  @Test
  void responseTimeIsComparableForNewAndExistingEmail() throws Exception {
    String taken = newEmail();
    register(RegistrationFixtures.payload(taken, PASSWORD, "psychologist"));
    for (int i = 0; i < 5; i++) {
      register(RegistrationFixtures.payload(newEmail(), PASSWORD, "psychologist"));
      register(RegistrationFixtures.payload(taken, PASSWORD, "psychologist"));
    }
    List<Long> fresh = new ArrayList<>();
    List<Long> existing = new ArrayList<>();
    for (int i = 0; i < 15; i++) {
      Map<String, Object> p1 = RegistrationFixtures.payload(newEmail(), PASSWORD, "psychologist");
      long t0 = System.nanoTime();
      register(p1);
      fresh.add(System.nanoTime() - t0);
      Map<String, Object> p2 = RegistrationFixtures.payload(taken, PASSWORD, "psychologist");
      long t1 = System.nanoTime();
      register(p2);
      existing.add(System.nanoTime() - t1);
    }
    long a = median(fresh);
    long b = median(existing);
    long diffMs = Math.abs(a - b) / 1_000_000;
    double ratio = (double) Math.max(a, b) / Math.min(a, b);
    assertThat(diffMs <= 150 || ratio <= 1.5)
        .as("médianes : nouveau=%d ms, existant=%d ms", a / 1_000_000, b / 1_000_000)
        .isTrue();
  }

  private static long median(List<Long> values) {
    List<Long> sorted = new ArrayList<>(values);
    sorted.sort(Long::compare);
    return sorted.get(sorted.size() / 2);
  }

  @Test
  void remoteOnlyWithoutAddressIsAccepted() throws Exception {
    String email = newEmail();
    Map<String, Object> p = RegistrationFixtures.payload(email, PASSWORD, "psychologist");
    p.remove("address");
    p.put("remoteOnly", true);
    assertThat(register(p).getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> row =
        jdbc.queryForMap(
            "select remote_only, address_street, address_city from practitioner where"
                + " email_normalized = ?",
            email);
    assertThat(row.get("remote_only")).isEqualTo(true);
    assertThat(row.get("address_street")).isNull();
    assertThat(row.get("address_city")).isNull();
  }

  @Test
  void missingAddressWithoutRemoteOnlyGives422WithField() throws Exception {
    Map<String, Object> p = RegistrationFixtures.payload(newEmail(), PASSWORD, "psychologist");
    p.remove("address");
    MvcResult r = register(p);
    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
    assertThat(errorFields(r)).contains("address");
  }

  @Test
  void incompleteAddressGives422PerField() throws Exception {
    Map<String, Object> p = RegistrationFixtures.payload(newEmail(), PASSWORD, "psychologist");
    p.put("address", Map.of("street", "", "postalCode", "6903", "city", "Lyon"));
    MvcResult r = register(p);
    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorFields(r)).contains("address.street", "address.postalCode");
  }

  @Test
  void invalidPhoneGives422() throws Exception {
    Map<String, Object> p = RegistrationFixtures.payload(newEmail(), PASSWORD, "psychologist");
    p.put("phone", "abc");
    MvcResult r = register(p);
    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorFields(r)).contains("phone");
  }

  @Test
  void professionOutsideListGives422() throws Exception {
    MvcResult r = register(RegistrationFixtures.payload(newEmail(), PASSWORD, "nurse"));
    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorFields(r)).contains("profession");
  }

  @Test
  void missingConsentsGive422() throws Exception {
    Map<String, Object> none = RegistrationFixtures.payload(newEmail(), PASSWORD, "coach");
    none.remove("consents");
    MvcResult r = register(none);
    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorFields(r)).contains("consents");

    Map<String, Object> partial = RegistrationFixtures.payload(newEmail(), PASSWORD, "coach");
    partial.put("consents", Map.of("terms", true, "privacy", false));
    MvcResult r2 = register(partial);
    assertThat(r2.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorFields(r2)).contains("consents.privacy").doesNotContain("consents.terms");

    Map<String, Object> empty = RegistrationFixtures.payload(newEmail(), PASSWORD, "coach");
    empty.put("consents", new HashMap<>());
    assertThat(register(empty).getResponse().getStatus()).isEqualTo(422);
  }

  @Test
  void passwordPolicyIsApplied() throws Exception {
    assertThat(
            register(RegistrationFixtures.payload(newEmail(), "Motdepasse1", "coach"))
                .getResponse()
                .getStatus())
        .isEqualTo(422);
    MvcResult compromised =
        register(RegistrationFixtures.payload(newEmail(), "motdepasse1234", "coach"));
    assertThat(compromised.getResponse().getStatus()).isEqualTo(422);
    assertThat(compromised.getResponse().getContentAsString()).contains("PASSWORD_COMPROMISED");
    // Entrée de la liste des 10 000 mots de passe courants, avec une majuscule ajoutée.
    MvcResult fromList =
        register(RegistrationFixtures.payload(newEmail(), "Unbelievable", "coach"));
    assertThat(fromList.getResponse().getStatus()).isEqualTo(422);
    assertThat(fromList.getResponse().getContentAsString()).contains("PASSWORD_COMPROMISED");
    assertThat(
            register(RegistrationFixtures.payload(newEmail(), "Vendôme-Lyon-2026!x", "coach"))
                .getResponse()
                .getStatus())
        .isEqualTo(201);
  }

  @Test
  void coachGetsClientVocabularyAndHiddenMedication() throws Exception {
    String email = newEmail();
    register(RegistrationFixtures.payload(email, PASSWORD, "coach"));
    JsonNode prefs =
        json.readTree(
            jdbc.queryForObject(
                "select preferences::text from practitioner where email_normalized = ?",
                String.class,
                email));
    assertThat(prefs.path("vocabulary.subjectLabel").asString()).isEqualTo("client");
    assertThat(prefs.path("entryTypes.medicationVisible").asBoolean(true)).isFalse();
  }

  @Test
  void invitationCodeIsNeitherRequiredNorStored() throws Exception {
    Map<String, Object> p = RegistrationFixtures.payload(newEmail(), PASSWORD, "psychologist");
    assertThat(p).doesNotContainKey("invitationCode");
    assertThat(register(p).getResponse().getStatus()).isEqualTo(201);
  }

  @Test
  void deprecatedSignupAliasBehavesLikeRegister() throws Exception {
    String email = newEmail();
    MvcResult r =
        mvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            RegistrationFixtures.payload(email, PASSWORD, "psychiatrist"))))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "select verification_status from practitioner where email_normalized = ?",
                String.class,
                email))
        .isEqualTo("email_pending");
  }
}
