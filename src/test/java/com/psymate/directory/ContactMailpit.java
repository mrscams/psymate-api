package com.psymate.directory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Lecture des e-mails reçus par Mailpit (l'envoi est asynchrone). */
public final class ContactMailpit {

  private final GenericContainer<?> mailpit;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();

  public ContactMailpit(GenericContainer<?> mailpit) {
    this.mailpit = mailpit;
  }

  private String get(String path) throws Exception {
    URI uri = URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path);
    return http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString())
        .body();
  }

  private JsonNode search(String to) throws Exception {
    String query = "to:" + to;
    return json.readTree(
        get("/api/v1/search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)));
  }

  /** Dernier message complet pour ce destinataire ; null après 10 s d'attente. */
  public JsonNode awaitMessage(String to) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (true) {
      JsonNode found = search(to);
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

  /** Nombre de messages pour ce destinataire, après une courte attente. */
  public int count(String to) throws Exception {
    Thread.sleep(1500);
    return search(to).path("messages").size();
  }
}
