package com.psymate.seed;

import com.psymate.seed.ApiClient.Response;
import com.psymate.seed.BookingScenario.Guest;
import com.psymate.seed.Plan.Practitioner;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Écrit la prise de rendez-vous en ligne de démonstration (M14-08) par l'API, comme le reste du
 * seed, à partir de {@link BookingScenario} :
 *
 * <ol>
 *   <li>approbation des deux comptes du seed, seule écriture SQL ({@link LocalApproval}, pourquoi
 *       dans sa documentation), seulement s'ils ne sont pas déjà approuvés ({@code GET
 *       /api/auth/session}) ;
 *   <li>fiche d'annuaire de chacun ({@code PUT /api/me/profile} puis {@code POST
 *       /api/me/profile/publish}), si elle n'est pas déjà publiée ;
 *   <li>réglages {@code booking.*} ({@code PATCH /api/me/preferences}), si {@code booking.enabled}
 *       n'est pas déjà vrai ;
 *   <li>chez Claire, trois demandes de visiteurs par le parcours public complet : {@code POST
 *       …/requests}, lien de vérification lu dans Mailpit comme pour la confirmation des comptes
 *       ({@link Accounts}), {@code POST /requests/verify} ; deux restent en attente, la troisième
 *       est confirmée par Claire ({@code POST /api/appointments/{id}/confirm}). Rien n'est écrit si
 *       Claire a déjà un rendez-vous d'origine {@code online} (annulé compris) autour
 *       d'aujourd'hui.
 * </ol>
 *
 * <p>Contrôle final, en lecture : fiche publique de réservation des deux praticiens ({@code 200}),
 * créneaux offerts chez Claire, demandes en attente ({@code GET /api/agenda/requests}).
 */
public final class BookingSeeder {

  private static final Pattern VERIFY_LINK =
      Pattern.compile("/rendez-vous/confirmer/([A-Za-z0-9_-]+)");
  private static final String PUBLIC = "/api/public/booking/practitioners/";

  /** Bilan de la prise de rendez-vous en ligne. */
  public record Result(
      String demoSlug,
      String secondSlug,
      List<String> approved,
      boolean requestsAlreadyPresent,
      int publicDemoStatus,
      int publicSecondStatus,
      int demoSlots,
      int secondSlots,
      int pendingRequests,
      int confirmedOnline) {

    /**
     * Critères de M14-08 : fiches offertes, créneaux, et les deux demandes si elles sont neuves.
     */
    public boolean ok() {
      return publicDemoStatus == 200
          && publicSecondStatus == 200
          && demoSlots > 0
          && secondSlots > 0
          && (requestsAlreadyPresent || (pendingRequests == 2 && confirmedOnline == 1));
    }

    public String describe() {
      return "Prise de rendez-vous en ligne : Claire /"
          + demoSlug
          + " (manual, "
          + demoSlots
          + " créneaux sur 28 jours, "
          + pendingRequests
          + " demandes en attente, "
          + confirmedOnline
          + " rendez-vous en ligne confirmé"
          + (requestsAlreadyPresent ? ", déjà présents" : "")
          + "), Karim /"
          + secondSlug
          + " (auto, "
          + secondSlots
          + " créneaux) ; fiches publiques "
          + publicDemoStatus
          + " et "
          + publicSecondStatus
          + (approved.isEmpty() ? "" : " ; approuvés en SQL : " + String.join(", ", approved))
          + (ok() ? " : OK" : " : ÉCHEC");
    }
  }

  private final String apiUrl;
  private final String mailpitUrl;
  private final LocalApproval approval;

  public BookingSeeder(String apiUrl, String mailpitUrl, LocalApproval approval) {
    this.apiUrl = apiUrl;
    this.mailpitUrl = mailpitUrl;
    this.approval = approval;
  }

  public Result seed(ApiClient claire, ApiClient karim) {
    List<String> approved =
        approveIfNeeded(Map.of(Practitioner.CLAIRE, claire, Practitioner.KARIM, karim));
    String demoSlug = publish(claire, Practitioner.CLAIRE);
    String secondSlug = publish(karim, Practitioner.KARIM);
    configure(claire, Practitioner.CLAIRE);
    configure(karim, Practitioner.KARIM);

    boolean already = hasOnlineAppointment(claire);
    if (!already) {
      writeRequests(claire, demoSlug);
    }

    ApiClient visitor = new ApiClient(apiUrl, 0);
    LocalDate today = LocalDate.now(BookingScenario.ZONE);
    return new Result(
        demoSlug,
        secondSlug,
        approved,
        already,
        visitor.get(PUBLIC + demoSlug).status(),
        visitor.get(PUBLIC + secondSlug).status(),
        slots(visitor, demoSlug, today, today.plusDays(28)).size(),
        slots(visitor, secondSlug, today, today.plusDays(28)).size(),
        pending(claire).size(),
        (int) online(claire).stream().filter(a -> "planned".equals(a.get("status"))).count());
  }

  // ---- approbation (seule écriture SQL)

  private List<String> approveIfNeeded(Map<Practitioner, ApiClient> clients) {
    List<String> todo = new ArrayList<>();
    for (Practitioner who : Practitioner.values()) {
      if (!"approved".equals(status(clients.get(who)))) {
        todo.add(Accounts.of(who).email());
      }
    }
    if (todo.isEmpty()) {
      return List.of();
    }
    List<String> done = approval.approve(todo);
    for (Practitioner who : Practitioner.values()) {
      String s = status(clients.get(who)); // relit le compte et rafraîchit la session
      if (!"approved".equals(s)) {
        throw new SeedException(
            "Compte "
                + Accounts.of(who).email()
                + " non approuvé après le SQL (statut "
                + s
                + ").");
      }
    }
    return done;
  }

  private static String status(ApiClient api) {
    return String.valueOf(api.get("/api/auth/session", 200).json().get("verificationStatus"));
  }

  // ---- fiche publique

  /** Publie la fiche si elle ne l'est pas ; renvoie le slug réel. */
  private static String publish(ApiClient api, Practitioner who) {
    Map<String, Object> profile = api.get("/api/me/profile", 200).json();
    if (!Boolean.TRUE.equals(profile.get("published"))) {
      api.put("/api/me/profile", BookingScenario.profile(who), 200);
      api.post("/api/me/profile/publish", null, 204);
      profile = api.get("/api/me/profile", 200).json();
    }
    Object slug = profile.get("slug");
    if (slug == null || !Boolean.TRUE.equals(profile.get("published"))) {
      throw new SeedException("Fiche de " + Accounts.of(who).email() + " non publiée.");
    }
    return slug.toString();
  }

  // ---- réglages booking.*

  private static void configure(ApiClient api, Practitioner who) {
    Map<String, Object> prefs = api.get("/api/me/preferences", 200).json();
    if (!Boolean.TRUE.equals(prefs.get("booking.enabled"))) {
      api.patch("/api/me/preferences", BookingScenario.preferences(who), 200);
    }
  }

  // ---- demandes de visiteurs (Claire)

  private void writeRequests(ApiClient claire, String slug) {
    ApiClient visitor = new ApiClient(apiUrl, 0);
    visitor.get(PUBLIC + slug, 200);
    LocalDate today = LocalDate.now(BookingScenario.ZONE);
    List<Instant> offered = slots(visitor, slug, today, today.plusDays(8));
    List<Instant> chosen = BookingScenario.choose(offered, Instant.now());
    List<Guest> guests = new ArrayList<>(BookingScenario.PENDING);
    guests.add(BookingScenario.CONFIRMED);
    for (int i = 0; i < guests.size(); i++) {
      request(visitor, slug, guests.get(i), chosen.get(i));
    }
    Instant confirmedAt = chosen.get(2);
    String id = null;
    for (Map<?, ?> a : pending(claire)) {
      if (a.get("guest") instanceof Map<?, ?> g
          && BookingScenario.CONFIRMED.email().equals(g.get("email"))
          && Instant.parse(String.valueOf(a.get("startsAt"))).equals(confirmedAt)) {
        id = String.valueOf(a.get("id"));
      }
    }
    if (id == null) {
      throw new SeedException(
          "Demande de " + BookingScenario.CONFIRMED.email() + " absente de /api/agenda/requests.");
    }
    claire.post("/api/appointments/" + id + "/confirm", null, 200);
  }

  /** Parcours public d'un visiteur : demande, lien lu dans Mailpit, vérification. */
  private void request(ApiClient visitor, String slug, Guest guest, Instant startsAt) {
    Set<String> before = messageIds(guest.email());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("startsAt", startsAt.toString());
    body.put("modality", guest.modality());
    body.put("firstName", guest.firstName());
    body.put("lastName", guest.lastName());
    body.put("email", guest.email());
    if (guest.phone() != null) {
      body.put("phone", guest.phone());
    }
    body.put("locale", "fr");
    body.put("consent", true);
    body.put("website", "");
    // formulaire affiché 30 s plus tôt : au-delà des 3 s du filtre anti-robot (G Q78)
    body.put(
        "formShownAt", Instant.now().minusSeconds(30).truncatedTo(ChronoUnit.SECONDS).toString());
    Response r = visitor.post(PUBLIC + slug + "/requests", body);
    if (r.status() == 429) {
      throw new SeedException(
          "Plafond de 5 demandes par heure et par IP atteint (G Q79) : relance dans une heure ou"
              + " redémarre l'API locale.");
    }
    if (r.status() != 202) {
      throw new ApiClient.ApiException("POST", PUBLIC + slug + "/requests", r);
    }
    String token = awaitVerifyToken(guest.email(), before);
    Map<String, Object> verify = new LinkedHashMap<>();
    verify.put("token", token);
    visitor.post("/api/public/booking/requests/verify", verify, 200);
  }

  private Set<String> messageIds(String email) {
    Set<String> ids = new HashSet<>();
    for (Map<?, ?> m : mailpitSearch(email)) {
      ids.add(String.valueOf(m.get("ID")));
    }
    return ids;
  }

  private List<Map<?, ?>> mailpitSearch(String email) {
    ApiClient mailpit = new ApiClient(mailpitUrl);
    String query = URLEncoder.encode("to:" + email, StandardCharsets.UTF_8);
    Response search = mailpit.get("/api/v1/search?query=" + query);
    List<Map<?, ?>> found = new ArrayList<>();
    if (search.status() == 200 && search.json().get("messages") instanceof List<?> list) {
      for (Object o : list) {
        if (o instanceof Map<?, ?> m && m.get("ID") != null) {
          found.add(m);
        }
      }
    }
    return found;
  }

  /** Jeton du lien de vérification d'un nouveau message (30 s au plus). */
  private String awaitVerifyToken(String email, Set<String> before) {
    ApiClient mailpit = new ApiClient(mailpitUrl);
    for (int attempt = 0; attempt < 60; attempt++) {
      for (Map<?, ?> m : mailpitSearch(email)) {
        if (before.contains(String.valueOf(m.get("ID")))) {
          continue;
        }
        Object text = mailpit.get("/api/v1/message/" + m.get("ID")).json().get("Text");
        Matcher matcher = VERIFY_LINK.matcher(text == null ? "" : text.toString());
        if (matcher.find()) {
          return matcher.group(1);
        }
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new SeedException("Interrompu pendant l'attente du lien de vérification", e);
      }
    }
    throw new SeedException(
        "Aucun lien de vérification pour " + email + " dans Mailpit (" + mailpitUrl + ").");
  }

  // ---- lectures

  /** Créneaux offerts de {@code from} à {@code to} inclus (dates du fuseau du praticien). */
  private static List<Instant> slots(ApiClient visitor, String slug, LocalDate from, LocalDate to) {
    Response r = visitor.get(PUBLIC + slug + "/slots?from=" + from + "&to=" + to);
    List<Instant> slots = new ArrayList<>();
    if (r.status() == 200 && r.json().get("slots") instanceof List<?> list) {
      for (Object o : list) {
        if (o instanceof Map<?, ?> s) {
          slots.add(Instant.parse(String.valueOf(s.get("startsAt"))));
        }
      }
    }
    return slots;
  }

  /** {@code GET /api/agenda/requests} : demandes en ligne en attente, à venir. */
  private static List<Map<?, ?>> pending(ApiClient api) {
    return maps(api.get("/api/agenda/requests", 200).json().get("appointments"));
  }

  /**
   * Rendez-vous d'origine online de 31 jours avant à 31 jours après aujourd'hui, annulés compris.
   */
  private static List<Map<?, ?>> online(ApiClient api) {
    Instant midnight =
        LocalDate.now(BookingScenario.ZONE).atStartOfDay(BookingScenario.ZONE).toInstant();
    String path =
        "/api/agenda?from="
            + midnight.minus(31, ChronoUnit.DAYS)
            + "&to="
            + midnight.plus(31, ChronoUnit.DAYS)
            + "&includeCancelled=true";
    List<Map<?, ?>> online = new ArrayList<>();
    for (Map<?, ?> a : maps(api.get(path, 200).json().get("appointments"))) {
      if ("online".equals(a.get("origin"))) {
        online.add(a);
      }
    }
    return online;
  }

  private static boolean hasOnlineAppointment(ApiClient api) {
    return !online(api).isEmpty();
  }

  private static List<Map<?, ?>> maps(Object value) {
    List<Map<?, ?>> list = new ArrayList<>();
    if (value instanceof List<?> l) {
      for (Object o : l) {
        if (o instanceof Map<?, ?> m) {
          list.add(m);
        }
      }
    }
    return list;
  }
}
