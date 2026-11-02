package com.psymate.identity.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher.MatchResult;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Journal d'audit applicatif (G Q29) : à chaque requête d'un praticien authentifié sur une
 * ressource patient, ajoute une ligne {@code patient.read} (GET, HEAD, OPTIONS) ou {@code
 * patient.write} (autres méthodes) dans {@code security_event}, par {@link AuditWriter} et hors du
 * thread de la requête. Routes couvertes : {@code /api/patients/{id}/**} (et {@code
 * /api/patients/{id}}), {@code /api/entries/{id}/**}, {@code /api/links/**}, {@code
 * /api/sessions/{id}/**}.
 *
 * <p>Placé après l'authentification dans la chaîne principale d'identity : une requête refusée en
 * amont (401, 403, 429) n'a rien lu et n'est pas journalisée. Le chemin est reconnu sur sa forme
 * décodée. Le praticien est lu dans le SecurityContext (son nom est son UUID).
 *
 * <p>Les {@code details} portent l'identifiant lu dans l'URL ({@code patientId}, {@code entryId},
 * {@code linkId} ou {@code sessionId} ; null s'il est absent ou n'est pas un UUID, le chemin décodé
 * complet étant dans {@code path}), pas un patient résolu : retrouver le patient d'une entrée, d'un
 * lien ou d'une séance exigerait de dépendre de clinical ou de linking, ce qu'identity n'a pas le
 * droit de faire. Le journal reste exploitable : l'identifiant d'entrée se rattache au patient par
 * une requête d'administration.
 *
 * <p>Ce filtre n'est volontairement pas un bean (Spring Boot l'enregistrerait aussi dans le
 * conteneur servlet, avant la sécurité) : la chaîne d'identity le construit.
 */
public final class AuditFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(AuditFilter.class);
  private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  /** Une route auditée : son motif et la clé de {@code details} qui reçoit l'identifiant. */
  private record Route(RequestMatcher matcher, String detailKey) {
    static Route of(String pattern, String detailKey) {
      return new Route(PathPatternRequestMatcher.withDefaults().matcher(pattern), detailKey);
    }
  }

  // L'ordre compte : /api/links/{id}/** avant le motif sans identifiant.
  private static final List<Route> ROUTES =
      List.of(
          Route.of("/api/patients/{id}/**", "patientId"),
          Route.of("/api/entries/{id}/**", "entryId"),
          Route.of("/api/links/{id}/**", "linkId"),
          Route.of("/api/links/**", "linkId"),
          Route.of("/api/sessions/{id}/**", "sessionId"));

  private final AuditWriter writer;

  public AuditFilter(AuditWriter writer) {
    this.writer = writer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    UUID practitioner = currentPractitioner();
    Match match = practitioner == null ? null : match(request);
    if (match == null) {
      chain.doFilter(request, response);
      return;
    }
    int status;
    try {
      chain.doFilter(request, response);
      status = response.getStatus();
    } catch (Throwable t) {
      // L'exception poursuit sa route ; la ligne d'audit porte le statut que verra le client (au
      // moins 500 : ce qui traverse le DispatcherServlet n'a pas été résolu en 4xx).
      record(request, Math.max(response.getStatus(), 500), practitioner, match);
      throw t;
    }
    record(request, status, practitioner, match);
  }

  private record Match(String detailKey, UUID resourceId) {}

  private static Match match(HttpServletRequest request) {
    for (Route route : ROUTES) {
      MatchResult result = route.matcher().matcher(request);
      if (result.isMatch()) {
        return new Match(route.detailKey(), uuidOrNull(result.getVariables().get("id")));
      }
    }
    return null;
  }

  private void record(HttpServletRequest request, int status, UUID practitioner, Match match) {
    try {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put(
          match.detailKey(), match.resourceId() == null ? null : match.resourceId().toString());
      details.put("method", request.getMethod());
      details.put("path", decodedPath(request));
      details.put("status", status);
      String kind =
          READ_METHODS.contains(request.getMethod())
              ? AuditEvent.PATIENT_READ
              : AuditEvent.PATIENT_WRITE;
      writer.publish(
          new AuditEvent(
              practitioner,
              kind,
              request.getRemoteAddr(),
              request.getHeader("User-Agent"),
              details,
              Instant.now()));
    } catch (RuntimeException e) {
      // L'audit ne fait jamais échouer la requête.
      LOG.warn("Audit impossible pour {} {}", request.getMethod(), request.getRequestURI(), e);
    }
  }

  /** UUID du praticien authentifié, {@code null} sinon (anonyme, nom qui n'est pas un UUID). */
  private static UUID currentPractitioner() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return null;
    }
    return uuidOrNull(auth.getName());
  }

  private static UUID uuidOrNull(String value) {
    if (value == null) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Chemin décodé dans l'application, sans contexte ni chaîne de requête. */
  private static String decodedPath(HttpServletRequest request) {
    String servletPath = request.getServletPath();
    String pathInfo = request.getPathInfo();
    String path = (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
    if (path.isEmpty()) {
      String raw = request.getRequestURI().substring(request.getContextPath().length());
      path = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
    }
    return path;
  }
}
