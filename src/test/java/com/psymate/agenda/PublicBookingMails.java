package com.psymate.agenda;

import com.psymate.testsupport.SharedMailpit;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;

/**
 * E-mails reçus par Mailpit pour un destinataire, filtrés par objet <b>exact</b> : la recherche de
 * Mailpit compare des sous-chaînes, et « Votre demande de rendez-vous » est contenu dans «
 * Confirmez votre demande de rendez-vous ». Attentes longues : la file d'envoi est partagée par
 * toute la JVM de tests.
 */
final class PublicBookingMails {

  private final GenericContainer<?> mailpit = SharedMailpit.container();
  private final HttpClient http = HttpClient.newHttpClient();

  private JsonNode get(String path) throws Exception {
    URI uri = URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path);
    String body =
        http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body();
    return PublicBookingFixtures.JSON.readTree(body);
  }

  /** Identifiants des messages de ce destinataire dont l'objet est exactement {@code subject}. */
  private List<String> ids(String to, String subject) throws Exception {
    JsonNode found =
        get("/api/v1/search?query=" + URLEncoder.encode("to:" + to, StandardCharsets.UTF_8));
    List<String> ids = new ArrayList<>();
    for (JsonNode m : found.path("messages")) {
      if (subject.equals(m.path("Subject").asString())) {
        ids.add(m.path("ID").asString());
      }
    }
    return ids;
  }

  /** Texte du dernier message de cet objet ; null après 30 s. */
  String awaitText(String to, String subject) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (true) {
      List<String> ids = ids(to, subject);
      if (!ids.isEmpty()) {
        return get("/api/v1/message/" + ids.get(0)).path("Text").asString();
      }
      if (System.nanoTime() > deadline) {
        return null;
      }
      Thread.sleep(100);
    }
  }

  /** Attend au moins {@code expected} messages de cet objet (30 s au plus) et rend leur nombre. */
  int awaitCount(String to, String subject, int expected) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    int n;
    while ((n = ids(to, subject).size()) < expected && System.nanoTime() < deadline) {
      Thread.sleep(100);
    }
    return n;
  }

  /**
   * Nombre de messages de cet objet après un court délai, pour vérifier qu'aucun envoi de plus
   * n'est parti (les envois attendus doivent être déjà arrivés).
   */
  int countAfterQuiet(String to, String subject) throws Exception {
    Thread.sleep(1500);
    return ids(to, subject).size();
  }

  /** Nombre de messages de ce destinataire, tous objets, après un court délai. */
  int countAllAfterQuiet(String to) throws Exception {
    Thread.sleep(1500);
    JsonNode found =
        get("/api/v1/search?query=" + URLEncoder.encode("to:" + to, StandardCharsets.UTF_8));
    return found.path("messages").size();
  }
}
