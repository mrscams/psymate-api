package com.psymate.identity.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.psymate.identity.auth.AbstractLoginIT;
import com.psymate.identity.auth.RegistrationFixtures;
import com.psymate.testsupport.MailpitClient;
import com.psymate.testsupport.SharedMailpit;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

/**
 * M10-06 : GET /api/me/security (sessions actives, dernière connexion) et POST
 * /api/me/sessions/{id}/revoke. Chaque test crée ses comptes et ne regarde que leurs sessions (base
 * partagée) ; les adresses de test sont sous 198.51.100.15x.
 */
class MeSecurityIT extends AbstractLoginIT {

  private static final String IP_SECURITY = "198.51.100.150";

  private final MailpitClient mails = new MailpitClient(SharedMailpit.container());

  private static final String CHROME_WINDOWS =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/124.0.0.0 Safari/537.36";
  private static final String FIREFOX_LINUX =
      "Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0";

  private Cookie loginWith(String email, String userAgent, boolean rememberMe) throws Exception {
    Map<String, Object> body =
        Map.of("email", email, "password", PASSWORD, "rememberMe", rememberMe);
    var request =
        MockMvcRequestBuilders.post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body))
            .with(
                r -> {
                  r.setRemoteAddr(IP_SECURITY);
                  return r;
                });
    if (userAgent != null) {
      request.header("User-Agent", userAgent);
    }
    MvcResult result = mvc.perform(request).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return result.getResponse().getCookie("SESSION");
  }

  private MvcResult getSecurity(Cookie session) throws Exception {
    return mvc.perform(get("/api/me/security").cookie(session)).andReturn();
  }

  private JsonNode security(Cookie session) throws Exception {
    MvcResult r = getSecurity(session);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return body(r);
  }

  private MvcResult revoke(Cookie session, String id) throws Exception {
    return post("/api/me/sessions/" + id + "/revoke", null, session, IP_SECURITY);
  }

  private static JsonNode sessionOf(JsonNode security, String device) {
    for (JsonNode s : security.path("sessions")) {
      if (!s.path("device").isNull() && device.equals(s.path("device").asString())) {
        return s;
      }
    }
    throw new AssertionError("aucune session pour " + device + " dans " + security);
  }

  private int meStatus(Cookie session) throws Exception {
    return mvc.perform(get("/api/auth/me").cookie(session)).andReturn().getResponse().getStatus();
  }

  @Test
  void listsOwnSessionsWithDeviceAndMarksTheCurrentOne() throws Exception {
    String email = account();
    Cookie chrome = loginWith(email, CHROME_WINDOWS, false);
    clock.advance(Duration.ofMinutes(1));
    Cookie firefox = loginWith(email, FIREFOX_LINUX, false);

    JsonNode fromChrome = security(chrome);
    assertThat(fromChrome.path("sessions")).hasSize(2);
    JsonNode chromeSession = sessionOf(fromChrome, "Chrome · Windows");
    JsonNode firefoxSession = sessionOf(fromChrome, "Firefox · Linux");
    assertThat(chromeSession.path("current").asBoolean()).isTrue();
    assertThat(firefoxSession.path("current").asBoolean(true)).isFalse();

    // La session courante suit l'appelant.
    JsonNode fromFirefox = security(firefox);
    assertThat(sessionOf(fromFirefox, "Firefox · Linux").path("current").asBoolean()).isTrue();
    assertThat(sessionOf(fromFirefox, "Chrome · Windows").path("current").asBoolean(true))
        .isFalse();

    // Champs documentés ; la ville n'est pas renseignée en V1.
    for (JsonNode s : fromChrome.path("sessions")) {
      assertThat(s.propertyNames())
          .containsExactlyInAnyOrder(
              "id", "createdAt", "lastAccessAt", "expiresAt", "current", "device", "city");
      assertThat(s.path("city").isNull()).isTrue();
      Instant created = Instant.parse(s.path("createdAt").asString());
      Instant expires = Instant.parse(s.path("expiresAt").asString());
      assertThat(Instant.parse(s.path("lastAccessAt").asString())).isAfterOrEqualTo(created);
      assertThat(expires).isAfter(created);
    }
    assertThat(fromChrome.propertyNames()).containsExactlyInAnyOrder("sessions", "lastLogin");
  }

  @Test
  void lastLoginIsThePreviousLoginNotTheCurrentOne() throws Exception {
    String email = account();
    Instant firstLoginAt = clock.instant();
    Cookie chrome = loginWith(email, CHROME_WINDOWS, false);
    clock.advance(Duration.ofMinutes(1));
    Cookie firefox = loginWith(email, FIREFOX_LINUX, false);

    // La seconde session lit la première connexion.
    JsonNode lastLogin = security(firefox).path("lastLogin");
    assertThat(lastLogin.propertyNames()).containsExactlyInAnyOrder("at", "device", "city");
    assertThat(lastLogin.path("device").asString()).isEqualTo("Chrome · Windows");
    assertThat(lastLogin.path("city").isNull()).isTrue();
    assertThat(Instant.parse(lastLogin.path("at").asString()))
        .isBetween(firstLoginAt.minusMillis(1), firstLoginAt.plusMillis(1));
    // La première session n'avait aucune connexion avant elle, même si une autre a suivi.
    assertThat(security(chrome).path("lastLogin").isNull()).isTrue();
  }

  @Test
  void firstLoginHasNoPreviousLogin() throws Exception {
    String email = account();
    Cookie session = loginWith(email, CHROME_WINDOWS, false);

    JsonNode security = security(session);

    assertThat(security.propertyNames()).contains("lastLogin");
    assertThat(security.path("lastLogin").isNull()).isTrue();
  }

  @Test
  void lastLoginIgnoresFailedAttemptsAndOtherAccounts() throws Exception {
    String email = account();
    String other = account();
    loginWith(email, CHROME_WINDOWS, false);
    clock.advance(Duration.ofMinutes(1));
    loginWith(other, FIREFOX_LINUX, false);
    clock.advance(Duration.ofMinutes(1));
    assertThat(login(email, "mauvais-mot-de-passe", null, IP_SECURITY).getResponse().getStatus())
        .isEqualTo(401);
    clock.advance(Duration.ofMinutes(1));
    Cookie second = loginWith(email, FIREFOX_LINUX, false);

    JsonNode lastLogin = security(second).path("lastLogin");
    assertThat(lastLogin.path("device").asString()).isEqualTo("Chrome · Windows");
  }

  @Test
  void loginsAtTheSameInstantAreOrderedDeterministicallyById() throws Exception {
    String email = account();
    // Horloge figée : les deux connexions ont exactement le même occurred_at.
    loginWith(email, CHROME_WINDOWS, false);
    loginWith(email, FIREFOX_LINUX, false);
    clock.advance(Duration.ofMinutes(1));
    Cookie third = loginWith(email, CHROME_WINDOWS, false);
    String expected =
        jdbc.queryForObject(
            "select details->>'device' from security_event where practitioner_id = ?"
                + " and kind = 'login_ok' and occurred_at < ? order by id desc limit 1",
            String.class,
            idOf(email),
            // Microseconde comme la colonne : l'événement de la troisième connexion est exclu.
            OffsetDateTime.ofInstant(
                clock.instant().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC));

    for (int i = 0; i < 3; i++) {
      assertThat(security(third).path("lastLogin").path("device").asString()).isEqualTo(expected);
    }
  }

  @Test
  void aSessionOpenedByEmailConfirmationReadsTheLastLoginBeforeIt() throws Exception {
    String email = newEmail();
    assertThat(
            post(
                    "/api/auth/register",
                    RegistrationFixtures.payload(email, PASSWORD, "psychologist"),
                    null,
                    IP_SECURITY)
                .getResponse()
                .getStatus())
        .isEqualTo(201);
    String token = mails.confirmationToken(email);
    // Connexion antérieure (le compte ne peut pas se connecter avant la confirmation : événement
    // posé directement). La confirmation ouvre une session sans écrire de login_ok.
    jdbc.update(
        "insert into security_event (id, practitioner_id, kind, details, occurred_at)"
            + " values (?, ?, 'login_ok', cast(? as jsonb), ?)",
        UUID.randomUUID(),
        idOf(email),
        "{\"device\":\"Safari · macOS\"}",
        OffsetDateTime.ofInstant(clock.instant().minus(Duration.ofDays(2)), ZoneOffset.UTC));
    clock.advance(Duration.ofMinutes(1));

    MvcResult confirm = post("/api/auth/email/confirm", Map.of("token", token), null, IP_SECURITY);
    assertThat(confirm.getResponse().getStatus()).isEqualTo(204);

    JsonNode lastLogin = security(confirm.getResponse().getCookie("SESSION")).path("lastLogin");
    assertThat(lastLogin.path("device").asString()).isEqualTo("Safari · macOS");
    assertThat(Instant.parse(lastLogin.path("at").asString()))
        .isBefore(clock.instant().minus(Duration.ofDays(1)));
  }

  @Test
  void aSessionWithoutRecognisedUserAgentHasNoDevice() throws Exception {
    String email = account();
    Cookie noHeader = loginWith(email, null, false);
    clock.advance(Duration.ofMinutes(1));
    Cookie unknown = loginWith(email, "curl/8.5.0", false);

    JsonNode security = security(noHeader);
    assertThat(security.path("sessions")).hasSize(2);
    for (JsonNode s : security.path("sessions")) {
      assertThat(s.path("device").isNull()).isTrue();
    }
    // La seconde session lit la première connexion, dont l'appareil n'est pas connu.
    JsonNode previous = security(unknown).path("lastLogin");
    assertThat(previous.isObject()).isTrue();
    assertThat(previous.path("device").isNull()).isTrue();
    assertThat(previous.path("city").isNull()).isTrue();
  }

  @Test
  void rememberMeSessionLastsThirtyDaysAndDefaultSessionTwelveHours() throws Exception {
    String email = account();
    Cookie defaultSession = loginWith(email, CHROME_WINDOWS, false);
    Cookie remembered = loginWith(email, FIREFOX_LINUX, true);

    JsonNode security = security(defaultSession);
    assertThat(lifetime(sessionOf(security, "Chrome · Windows"))).isEqualTo(Duration.ofHours(12));
    assertThat(lifetime(sessionOf(security, "Firefox · Linux"))).isEqualTo(Duration.ofDays(30));
    assertThat(security(remembered).path("sessions")).hasSize(2);
  }

  private static Duration lifetime(JsonNode session) {
    Instant last = Instant.parse(session.path("lastAccessAt").asString());
    Instant expires = Instant.parse(session.path("expiresAt").asString());
    return Duration.between(last, expires).truncatedTo(ChronoUnit.SECONDS);
  }

  @Test
  void mostRecentlyUsedSessionComesFirstAndExpiredOnesAreHidden() throws Exception {
    String email = account();
    Cookie first = loginWith(email, CHROME_WINDOWS, false);
    Cookie second = loginWith(email, FIREFOX_LINUX, false);
    JsonNode initial = security(first);
    String firstId = sessionOf(initial, "Chrome · Windows").path("id").asString();
    String secondId = sessionOf(initial, "Firefox · Linux").path("id").asString();

    // La première session a servi il y a une heure : la seconde passe devant.
    jdbc.update(
        "update spring_session set last_access_time = ? where primary_id = ?",
        System.currentTimeMillis() - 3_600_000L,
        firstId);
    // Le test ne fait que lire la liste avec le cookie de la seconde session.
    JsonNode ordered = security(second);
    assertThat(ordered.path("sessions").get(0).path("id").asString()).isEqualTo(secondId);
    assertThat(ordered.path("sessions").get(1).path("id").asString()).isEqualTo(firstId);

    // Expirée mais pas encore nettoyée par Spring Session : masquée et non révocable.
    jdbc.update("update spring_session set expiry_time = 1 where primary_id = ?", firstId);
    JsonNode afterExpiry = security(second);
    assertThat(afterExpiry.path("sessions")).hasSize(1);
    assertThat(afterExpiry.path("sessions").get(0).path("id").asString()).isEqualTo(secondId);
    assertThat(revoke(second, firstId).getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  void revokingAnotherSessionClosesItImmediately() throws Exception {
    String email = account();
    Cookie chrome = loginWith(email, CHROME_WINDOWS, false);
    Cookie firefox = loginWith(email, FIREFOX_LINUX, false);
    String firefoxId = sessionOf(security(chrome), "Firefox · Linux").path("id").asString();
    assertThat(meStatus(firefox)).isEqualTo(200);
    assertThat(attributeRows(firefoxId)).isPositive();

    MvcResult r = revoke(chrome, firefoxId);

    assertThat(r.getResponse().getStatus()).isEqualTo(204);
    // Plus aucune donnée de la session révoquée (contexte de sécurité compris) en base.
    assertThat(attributeRows(firefoxId)).isZero();
    // L'ancien cookie ne passe plus : 401, tout de suite (suppression de la ligne de session).
    assertThat(meStatus(firefox)).isEqualTo(401);
    assertThat(getSecurity(firefox).getResponse().getStatus()).isEqualTo(401);
    assertThat(meStatus(chrome)).isEqualTo(200);
    JsonNode remaining = security(chrome);
    assertThat(remaining.path("sessions")).hasSize(1);
    assertThat(remaining.path("sessions").get(0).path("current").asBoolean()).isTrue();
    // Consigné dans le journal de sécurité, sans adresse dans la réponse.
    assertThat(events(email, "session_revoked")).isEqualTo(1);
    assertThat(r.getResponse().getContentAsString()).isEmpty();
    // Une seconde révocation de la même session : elle n'existe plus.
    assertThat(revoke(chrome, firefoxId).getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  void revokingTheCurrentSessionIsRefusedWithConflict() throws Exception {
    String email = account();
    Cookie chrome = loginWith(email, CHROME_WINDOWS, false);
    String chromeId = sessionOf(security(chrome), "Chrome · Windows").path("id").asString();

    MvcResult r = revoke(chrome, chromeId);

    assertThat(r.getResponse().getStatus()).isEqualTo(409);
    assertThat(body(r).path("code").asString()).isEqualTo("CURRENT_SESSION");
    assertThat(meStatus(chrome)).isEqualTo(200);
    assertThat(security(chrome).path("sessions")).hasSize(1);
    assertThat(events(email, "session_revoked")).isZero();
  }

  @Test
  void unknownOrMalformedIdsAreNotFound() throws Exception {
    String email = account();
    Cookie chrome = loginWith(email, CHROME_WINDOWS, false);

    MvcResult unknown = revoke(chrome, "11111111-2222-3333-4444-555555555555");
    assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
    assertThat(body(unknown).path("code").asString()).isEqualTo("SESSION_NOT_FOUND");
    assertThat(revoke(chrome, "pas-un-uuid").getResponse().getStatus()).isEqualTo(404);
    assertThat(meStatus(chrome)).isEqualTo(200);
  }

  @Test
  void aPractitionerNeverSeesNorRevokesAnotherPractitionersSessions() throws Exception {
    String alice = account();
    String bob = account();
    Cookie aliceChrome = loginWith(alice, CHROME_WINDOWS, false);
    Cookie bobFirefox = loginWith(bob, FIREFOX_LINUX, false);
    String bobSessionId = sessionOf(security(bobFirefox), "Firefox · Linux").path("id").asString();

    JsonNode aliceView = security(aliceChrome);
    List<String> aliceIds = new ArrayList<>();
    aliceView.path("sessions").forEach(s -> aliceIds.add(s.path("id").asString()));
    assertThat(aliceIds).hasSize(1).doesNotContain(bobSessionId);
    assertThat(aliceView.toString()).doesNotContain("Firefox");
    // Première connexion d'Alice : rien avant elle, et rien de celle de Bob.
    assertThat(aliceView.path("lastLogin").isNull()).isTrue();

    // Même réponse qu'un id inconnu (même code, même détail : rien ne révèle l'existence de la
    // session de l'autre), et cette session reste ouverte.
    MvcResult foreign = revoke(aliceChrome, bobSessionId);
    MvcResult unknown = revoke(aliceChrome, "11111111-2222-3333-4444-555555555555");
    assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
    assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
    assertThat(body(foreign).path("code").asString())
        .isEqualTo("SESSION_NOT_FOUND")
        .isEqualTo(body(unknown).path("code").asString());
    assertThat(body(foreign).path("detail").asString())
        .isNotBlank()
        .isEqualTo(body(unknown).path("detail").asString());
    assertThat(body(foreign).path("title").asString())
        .isEqualTo(body(unknown).path("title").asString());
    assertThat(meStatus(bobFirefox)).isEqualTo(200);
    assertThat(security(bobFirefox).path("sessions")).hasSize(1);
  }

  @Test
  void responseNeverCarriesAnIpAddressARawUserAgentOrTheCookieId() throws Exception {
    String email = account();
    Cookie chrome = loginWith(email, CHROME_WINDOWS, false);
    loginWith(email, FIREFOX_LINUX, false);

    String raw = getSecurity(chrome).getResponse().getContentAsString();

    assertThat(raw)
        .doesNotContain(IP_SECURITY)
        .doesNotContain("198.51.100")
        .doesNotContain("Mozilla")
        .doesNotContain("AppleWebKit")
        .doesNotContain("Gecko")
        .doesNotContain("rv:125")
        .doesNotContain(chrome.getValue());
    // Pas non plus l'identifiant HTTP de la session (cookie décodé).
    String httpSessionId =
        new String(Base64.getDecoder().decode(chrome.getValue()), StandardCharsets.UTF_8);
    assertThat(raw).doesNotContain(httpSessionId);
  }

  @Test
  void theExposedIdIsNotAUsableSessionCookie() throws Exception {
    String email = account();
    Cookie chrome = loginWith(email, CHROME_WINDOWS, false);
    String id = sessionOf(security(chrome), "Chrome · Windows").path("id").asString();

    // Témoin : le même encodage appliqué au vrai identifiant HTTP ouvre bien la session.
    String httpSessionId =
        new String(Base64.getDecoder().decode(chrome.getValue()), StandardCharsets.UTF_8);
    assertThat(meStatus(cookieFor(httpSessionId))).isEqualTo(200);
    // L'id exposé, présenté comme cookie, n'authentifie personne.
    assertThat(meStatus(cookieFor(id))).isEqualTo(401);
    assertThat(getSecurity(cookieFor(id)).getResponse().getStatus()).isEqualTo(401);
  }

  private static Cookie cookieFor(String sessionId) {
    return new Cookie(
        "SESSION", Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8)));
  }

  private int attributeRows(String primaryId) {
    return jdbc.queryForObject(
        "select count(*) from spring_session_attributes where session_primary_id = ?",
        Integer.class,
        primaryId);
  }

  @Test
  void securityRoutesRequireASession() throws Exception {
    assertThat(mvc.perform(get("/api/me/security")).andReturn().getResponse().getStatus())
        .isEqualTo(401);
    assertThat(
            post("/api/me/sessions/11111111-2222-3333-4444-555555555555/revoke", null, null, IP)
                .getResponse()
                .getStatus())
        .isEqualTo(401);
  }
}
