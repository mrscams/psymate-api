package com.psymate.seed;

import com.psymate.seed.ApiClient.Response;
import com.psymate.seed.Plan.Practitioner;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Praticiens fictifs du seed : connexion, sinon inscription + confirmation par e-mail (Mailpit) +
 * connexion. Les comptes sont documentés dans tools/seed/README.md ; le mot de passe est celui de
 * développement, sans valeur hors du poste local.
 */
public final class Accounts {

  /** Mot de passe unique des comptes du seed (≥ 12 caractères, sans nom ni e-mail dedans). */
  public static final String PASSWORD = "Donnees-Fictives-2026";

  private static final String PHONE = "+33600000001";
  private static final Pattern TOKEN = Pattern.compile("confirmer-email/([A-Za-z0-9_.~%-]+)");

  /** Identité d'un praticien du seed. */
  public record Account(
      Practitioner who, String email, String firstName, String lastName, String profession) {}

  public static Account of(Practitioner who) {
    return switch (who) {
      case CLAIRE ->
          new Account(who, "claire.seed@example.test", "Claire", "Martin", "psychologist");
      case KARIM -> new Account(who, "karim.seed@example.test", "Karim", "Haddad", "psychiatrist");
    };
  }

  private final String apiUrl;
  private final String mailpitUrl;

  public Accounts(String apiUrl, String mailpitUrl) {
    this.apiUrl = apiUrl;
    this.mailpitUrl = mailpitUrl;
  }

  /** Ouvre une NOUVELLE session connectée pour ce praticien, en créant le compte au besoin. */
  public ApiClient login(Practitioner who) {
    Account account = of(who);
    ApiClient client = new ApiClient(apiUrl);
    Response r = tryLogin(client, account);
    if (r.status() == 200) {
      return client;
    }
    if (r.status() == 401) {
      register(client, account);
      confirm(client, account);
    } else if (r.status() == 403) {
      confirm(client, account);
    } else {
      throw new SeedException(
          "Connexion de " + account.email() + " impossible : " + ApiClient.describe(r));
    }
    r = tryLogin(client, account);
    if (r.status() != 200) {
      throw new SeedException(
          "Connexion de "
              + account.email()
              + " impossible après inscription : "
              + ApiClient.describe(r));
    }
    return client;
  }

  private static Response tryLogin(ApiClient client, Account account) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", account.email());
    body.put("password", PASSWORD);
    return client.post("/api/auth/login", body);
  }

  private static void register(ApiClient client, Account account) {
    Map<String, Object> consents = new LinkedHashMap<>();
    consents.put("terms", true);
    consents.put("privacy", true);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", account.email());
    body.put("password", PASSWORD);
    body.put("firstName", account.firstName());
    body.put("lastName", account.lastName());
    body.put("profession", account.profession());
    body.put("phone", PHONE);
    body.put("remoteOnly", true);
    body.put("consents", consents);
    client.post("/api/auth/register", body, 200, 201);
  }

  /** Confirme l'e-mail avec le jeton lu dans Mailpit (le plus récent message du destinataire). */
  private void confirm(ApiClient client, Account account) {
    ApiClient mailpit = new ApiClient(mailpitUrl);
    String token = null;
    for (int attempt = 0; attempt < 30 && token == null; attempt++) {
      token = findToken(mailpit, account.email());
      if (token == null) {
        if (attempt == 5) {
          register(
              client, account); // le compte existe peut-être sans mail : demande un nouvel envoi
        }
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new SeedException("Interrompu pendant l'attente du mail de confirmation", e);
        }
      }
    }
    if (token == null) {
      throw new SeedException(
          "Aucun e-mail de confirmation pour "
              + account.email()
              + " dans Mailpit ("
              + mailpitUrl
              + ").");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("token", token);
    client.post("/api/auth/email/confirm", body, 200, 204);
  }

  private static String findToken(ApiClient mailpit, String email) {
    String query = URLEncoder.encode("to:" + email, StandardCharsets.UTF_8);
    Response search = mailpit.get("/api/v1/search?query=" + query);
    if (search.status() != 200) {
      return null;
    }
    Object messages = search.json().get("messages");
    if (!(messages instanceof List<?> list) || list.isEmpty()) {
      return null;
    }
    for (Object m : list) {
      if (m instanceof Map<?, ?> msg && msg.get("ID") != null) {
        Response full = mailpit.get("/api/v1/message/" + msg.get("ID"));
        Object text = full.json().get("Text");
        if (text != null) {
          Matcher matcher = TOKEN.matcher(text.toString());
          if (matcher.find()) {
            return matcher.group(1);
          }
        }
      }
    }
    return null;
  }
}
