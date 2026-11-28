package com.psymate.identity.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.api.PractitionerFacade.IssuerView;
import com.psymate.identity.auth.AbstractLoginIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * M15-03 (G Q85, Q95) : identité d'émetteur d'un praticien et vérification de son mot de passe,
 * exposées aux autres modules par {@link PractitionerFacade}. La vérification n'a aucun effet de
 * bord : ni compteur de tentatives, ni verrou, ni événement de sécurité, ni journal.
 */
@ExtendWith(OutputCaptureExtension.class)
class PractitionerFacadeIT extends AbstractLoginIT {

  @Autowired private PractitionerFacade facade;
  @Autowired private PasswordEncoder encoder;

  @Test
  void issuerOfReturnsThePractitionersFields() throws Exception {
    String email = account("approved");
    UUID id = idOf(email);
    jdbc.update("update practitioner set professional_number = '689712345' where id = ?", id);
    Map<String, Object> row =
        jdbc.queryForMap("select display_name, phone from practitioner where id = ?", id);

    IssuerView issuer = facade.issuerOf(id).orElseThrow();

    assertThat(issuer)
        .isEqualTo(
            new IssuerView(
                id,
                "Maya",
                "Kaddour",
                (String) row.get("display_name"),
                "psychologist",
                "689712345",
                "12 rue des Lilas",
                "69003",
                "Lyon",
                (String) row.get("phone"),
                email));
    assertThat(issuer.displayName()).isNotBlank();
    assertThat(issuer.phone()).isNotBlank();
  }

  @Test
  void issuerOfLeavesMissingFieldsNull() throws Exception {
    UUID id = idOf(account("approved"));
    jdbc.update(
        "update practitioner set professional_number = null, phone = null, address_street = null,"
            + " address_postal_code = null, address_city = null where id = ?",
        id);

    IssuerView issuer = facade.issuerOf(id).orElseThrow();

    assertThat(issuer.professionalNumber()).isNull();
    assertThat(issuer.phone()).isNull();
    assertThat(issuer.addressStreet()).isNull();
    assertThat(issuer.addressPostalCode()).isNull();
    assertThat(issuer.addressCity()).isNull();
    assertThat(issuer.lastName()).isEqualTo("Kaddour");
  }

  @Test
  void issuerOfIsEmptyForAnUnknownPractitioner() {
    assertThat(facade.issuerOf(UUID.randomUUID())).isEmpty();
  }

  @Test
  void passwordMatchesOnlyTheCurrentPassword() throws Exception {
    UUID id = idOf(account("approved"));

    assertThat(facade.passwordMatches(id, PASSWORD)).isTrue();
    assertThat(facade.passwordMatches(id, new StringBuilder(PASSWORD))).isTrue();
    assertThat(facade.passwordMatches(id, PASSWORD + "x")).isFalse();
    assertThat(facade.passwordMatches(id, PASSWORD.toLowerCase())).isFalse();
    assertThat(facade.passwordMatches(id, "")).isFalse();
    assertThat(facade.passwordMatches(id, null)).isFalse();
  }

  @Test
  void passwordMatchesIsFalseForAnUnknownPractitioner() {
    assertThat(facade.passwordMatches(UUID.randomUUID(), PASSWORD)).isFalse();
    assertThat(facade.passwordMatches(null, PASSWORD)).isFalse();
  }

  @Test
  void passwordMatchesReadsTheCurrentHashWithTheLoginEncoder() throws Exception {
    UUID id = idOf(account("approved"));
    String next = "Autre-Tournesol-77";
    jdbc.update("update practitioner set password_hash = ? where id = ?", encoder.encode(next), id);

    assertThat(facade.passwordMatches(id, next)).isTrue();
    assertThat(facade.passwordMatches(id, PASSWORD)).isFalse();
  }

  @Test
  void eachPractitionerIsReadOnItsOwn() throws Exception {
    UUID a = idOf(account("approved"));
    UUID b = idOf(account("approved"));
    String passwordOfB = "Autre-Tournesol-88";
    jdbc.update(
        "update practitioner set password_hash = ?, last_name = 'Lambert',"
            + " professional_number = '109876543' where id = ?",
        encoder.encode(passwordOfB),
        b);

    assertThat(facade.passwordMatches(a, passwordOfB)).isFalse();
    assertThat(facade.passwordMatches(b, PASSWORD)).isFalse();
    assertThat(facade.passwordMatches(b, passwordOfB)).isTrue();
    IssuerView issuerA = facade.issuerOf(a).orElseThrow();
    assertThat(issuerA.id()).isEqualTo(a);
    assertThat(issuerA.lastName()).isEqualTo("Kaddour");
    assertThat(issuerA.professionalNumber()).isNull();
    assertThat(facade.issuerOf(b).orElseThrow().lastName()).isEqualTo("Lambert");
  }

  @Test
  void wrongPasswordsChangeNeitherTheAccountNorTheLoginCounters(CapturedOutput output)
      throws Exception {
    String email = account("approved");
    UUID id = idOf(email);
    Map<String, Object> before = accountState(id);
    int eventsBefore = securityEvents(id);
    String wrong = "Mauvais-Essai-" + UUID.randomUUID();

    for (int i = 0; i < 10; i++) {
      assertThat(facade.passwordMatches(id, wrong)).isFalse();
    }
    assertThat(facade.passwordMatches(id, PASSWORD)).isTrue();

    assertThat(accountState(id)).isEqualTo(before);
    assertThat(((Number) accountState(id).get("failed_logins")).intValue()).isZero();
    assertThat(accountState(id)).containsEntry("locked_until", null);
    assertThat(securityEvents(id)).isEqualTo(eventsBefore);
    // Dix échecs dépassent le seuil de connexion (5) : la connexion reste ouverte.
    assertThat(login(email, PASSWORD, null, IP).getResponse().getStatus()).isEqualTo(200);
    assertThat(output.getAll()).doesNotContain(wrong);
  }

  private Map<String, Object> accountState(UUID id) {
    return jdbc.queryForMap(
        "select verification_status, failed_logins, locked_until, password_hash, last_login_at,"
            + " preferences::text as preferences from practitioner where id = ?",
        id);
  }

  private int securityEvents(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from security_event where practitioner_id = ?", Integer.class, id);
  }
}
