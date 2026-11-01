package com.psymate.seed;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client HTTP de l'API : un client = une session (son propre pot de cookies), donc sa propre
 * enveloppe de limitation de débit (600 requêtes par minute et par session, G Q24). Un 429 est
 * rejoué après {@code Retry-After} secondes, au plus {@value #MAX_RETRIES} fois.
 */
public final class ApiClient {

  static final int MAX_RETRIES = 20;

  /** Réponse brute. */
  public record Response(int status, String body) {

    public Map<String, Object> json() {
      return Json.parseObject(body);
    }

    /** Corps JSON quelconque (objet, tableau...). */
    public Object value() {
      return Json.parse(body);
    }

    boolean is(int... codes) {
      for (int c : codes) {
        if (status == c) {
          return true;
        }
      }
      return false;
    }
  }

  /** Erreur de l'API (statut inattendu) avec le corps problem+json lisible. */
  public static final class ApiException extends SeedException {
    private final int status;

    ApiException(String method, String path, Response response) {
      super(method + " " + path + " : " + describe(response));
      this.status = response.status();
    }

    public int status() {
      return status;
    }
  }

  private final String base;
  private final HttpClient http;

  // pot de cookies maison : la session est posée avec l attribut Secure, que java.net.CookieManager
  // refuse de renvoyer en http (l API locale n a pas de TLS)
  private final Map<String, String> cookies = new java.util.concurrent.ConcurrentHashMap<>();

  public ApiClient(String baseUrl) {
    this.base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
  }

  public Response get(String path) {
    return send("GET", path, null);
  }

  public Response post(String path, Object body) {
    return send("POST", path, body);
  }

  public Response patch(String path, Object body) {
    return send("PATCH", path, body);
  }

  public Response delete(String path) {
    return send("DELETE", path, null);
  }

  /** Comme {@link #get} mais lève {@link ApiException} si le statut n'est pas l'un des attendus. */
  public Response get(String path, int... expected) {
    return check("GET", path, get(path), expected);
  }

  public Response post(String path, Object body, int... expected) {
    return check("POST", path, post(path, body), expected);
  }

  public Response patch(String path, Object body, int... expected) {
    return check("PATCH", path, patch(path, body), expected);
  }

  private Response check(String method, String path, Response response, int... expected) {
    if (!response.is(expected)) {
      throw new ApiException(method, path, response);
    }
    return response;
  }

  private Response send(String method, String path, Object body) {
    for (int attempt = 0; ; attempt++) {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(base + path))
              .timeout(Duration.ofSeconds(120))
              .header("Accept", "application/json, application/problem+json")
              .header("Accept-Language", "fr");
      if (!cookies.isEmpty()) {
        builder.header("Cookie", cookieHeader());
      }
      if (body == null) {
        builder.method(method, HttpRequest.BodyPublishers.noBody());
      } else {
        builder
            .header("Content-Type", "application/json; charset=UTF-8")
            .method(
                method,
                HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8));
      }
      HttpResponse<String> response;
      try {
        response =
            http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new SeedException(
            "Appel impossible : " + method + " " + base + path + " (" + e + ")", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new SeedException("Interrompu pendant " + method + " " + path, e);
      }
      storeCookies(response);
      if (response.statusCode() == 429 && attempt < MAX_RETRIES) {
        pause(response.headers().firstValue("Retry-After").orElse("5"));
        continue;
      }
      return new Response(response.statusCode(), response.body());
    }
  }

  private String cookieHeader() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> c : cookies.entrySet()) {
      if (sb.length() > 0) {
        sb.append("; ");
      }
      sb.append(c.getKey()).append('=').append(c.getValue());
    }
    return sb.toString();
  }

  /** Retient les cookies de la réponse (nom=valeur) ; une valeur vide ou Max-Age=0 l'efface. */
  private void storeCookies(HttpResponse<String> response) {
    for (String header : response.headers().allValues("Set-Cookie")) {
      String[] parts = header.split(";");
      int eq = parts[0].indexOf('=');
      if (eq <= 0) {
        continue;
      }
      String name = parts[0].substring(0, eq).trim();
      String value = parts[0].substring(eq + 1).trim();
      boolean expired = value.isEmpty();
      for (int i = 1; i < parts.length; i++) {
        if (parts[i].trim().equalsIgnoreCase("Max-Age=0")) {
          expired = true;
        }
      }
      if (expired) {
        cookies.remove(name);
      } else {
        cookies.put(name, value);
      }
    }
  }

  private static void pause(String retryAfter) {
    long seconds;
    try {
      seconds = Math.max(1, Math.min(120, Long.parseLong(retryAfter.trim())));
    } catch (NumberFormatException e) {
      seconds = 5;
    }
    try {
      Thread.sleep(seconds * 1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SeedException("Interrompu pendant l'attente du Retry-After", e);
    }
  }

  /** Résumé d'une réponse d'erreur : statut, titre, détail et erreurs par champ. */
  static String describe(Response response) {
    StringBuilder sb = new StringBuilder("HTTP ").append(response.status());
    try {
      Map<String, Object> problem = response.json();
      for (String key : List.of("title", "code", "detail")) {
        Object v = problem.get(key);
        if (v != null) {
          sb.append(" ").append(key).append("=").append(v);
        }
      }
      Object errors = problem.get("errors");
      if (errors != null) {
        sb.append(" errors=").append(Json.write(errors));
      }
      if (problem.isEmpty()) {
        sb.append(" ").append(response.body());
      }
    } catch (RuntimeException e) {
      sb.append(" ").append(response.body());
    }
    return sb.toString();
  }
}
