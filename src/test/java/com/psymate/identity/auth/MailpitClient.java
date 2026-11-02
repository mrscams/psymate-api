package com.psymate.identity.auth;

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

/** Lecture des e-mails reçus par Mailpit dans les tests (l'envoi est asynchrone). */
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

  private JsonNode search(String to, String subject) throws Exception {
    String query = "to:" + to + " subject:\"" + subject + "\"";
    return json.readTree(
        get("/api/v1/search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)));
  }

  /** Texte du dernier e-mail pour ce destinataire et ce sujet ; null après 10 s d'attente. */
  public String lastText(String to, String subject) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (true) {
      JsonNode found = search(to, subject);
      if (!found.path("messages").isEmpty()) {
        String id = found.path("messages").get(0).path("ID").asString();
        return json.readTree(get("/api/v1/message/" + id)).path("Text").asString();
      }
      if (System.nanoTime() > deadline) {
        return null;
      }
      Thread.sleep(100);
    }
  }

  /** Nombre d'e-mails correspondants, après une courte attente pour laisser partir l'asynchrone. */
  public int count(String to, String subject) throws Exception {
    Thread.sleep(1500);
    return search(to, subject).path("messages").size();
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
