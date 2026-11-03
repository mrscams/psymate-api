package com.psymate.testsupport;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lecture des e-mails reçus par Mailpit (API HTTP), par destinataire : le Mailpit est partagé par
 * toute la tranche ({@link SharedMailpit}), chaque test ne regarde que ses adresses.
 */
public final class MailpitClient {

  private final GenericContainer<?> mailpit;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();

  public MailpitClient(GenericContainer<?> mailpit) {
    this.mailpit = mailpit;
  }

  private String get(String path) throws Exception {
    URI uri = URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path);
    return http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString())
        .body();
  }

  private JsonNode search(String query) throws Exception {
    return json.readTree(
        get("/api/v1/search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)));
  }

  private static String query(String to, String subject) {
    return "to:" + to + " subject:\"" + subject + "\"";
  }

  /** Dernier message complet trouvé par la requête ; null après l'attente. */
  private JsonNode awaitFirst(String query, Duration wait) throws Exception {
    long deadline = System.nanoTime() + wait.toNanos();
    while (true) {
      JsonNode found = search(query);
      if (!found.path("messages").isEmpty()) {
        String id = found.path("messages").get(0).path("ID").asString();
        return json.readTree(get("/api/v1/message/" + id));
      }
      if (System.nanoTime() > deadline) {
        return null;
      }
      Thread.sleep(100);
    }
  }

  /** Texte du dernier e-mail pour ce destinataire et ce sujet ; null après 10 s d'attente. */
  public String lastText(String to, String subject) throws Exception {
    return lastText(to, subject, Duration.ofSeconds(10));
  }

  /** Comme lastText, avec une attente maximale choisie (envoi lent ou file d'envoi chargée). */
  public String lastText(String to, String subject, Duration wait) throws Exception {
    JsonNode message = awaitFirst(query(to, subject), wait);
    return message == null ? null : message.path("Text").asString();
  }

  /** Dernier message complet pour ce destinataire ; null après 10 s d'attente. */
  public JsonNode awaitMessage(String to) throws Exception {
    return awaitFirst("to:" + to, Duration.ofSeconds(10));
  }

  /** Nombre d'e-mails correspondants, après une courte attente pour laisser partir l'asynchrone. */
  public int count(String to, String subject) throws Exception {
    Thread.sleep(1500);
    return search(query(to, subject)).path("messages").size();
  }

  /** Nombre de messages pour ce destinataire, après une courte attente. */
  public int count(String to) throws Exception {
    Thread.sleep(1500);
    return search("to:" + to).path("messages").size();
  }

  /** Dernier jeton de confirmation (lien /confirmer-email/{token}) reçu par l'adresse. */
  public String confirmationToken(String to) throws Exception {
    String text = lastText(to, "confirmez");
    if (text == null) {
      return null;
    }
    Matcher m = Pattern.compile("/confirmer-email/([A-Za-z0-9_-]+)").matcher(text);
    return m.find() ? m.group(1) : null;
  }
}
