package com.psymate.identity.auth.service;

import com.psymate.identity.auth.model.ActiveSession;
import com.psymate.identity.auth.model.CurrentSessionException;
import com.psymate.identity.auth.model.SecurityOverview;
import com.psymate.identity.auth.model.SessionNotFoundException;
import com.psymate.identity.auth.model.UserAgents;
import com.psymate.identity.auth.repository.PractitionerRepository;
import com.psymate.identity.repository.SessionNativeQueries.ActiveSessionRow;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Sessions actives et dernière connexion du praticien (M10-06, fiche 18). Les sessions sont celles
 * de Spring Session JDBC : {@code spring_session.principal_name} porte l'id du praticien, donc un
 * praticien ne voit et ne révoque jamais que les siennes. L'identifiant exposé est la clé interne
 * de la ligne, jamais l'identifiant HTTP que porte le cookie ; aucune adresse IP n'est lue ni
 * rendue (A9 R7) et la ville n'est pas renseignée en V1 (aucune géolocalisation).
 */
@Service
public class SessionSecurityService {

  private final PractitionerRepository practitioners;
  private final FindByIndexNameSessionRepository<? extends Session> sessions;
  private final Clock clock;

  SessionSecurityService(
      PractitionerRepository practitioners,
      FindByIndexNameSessionRepository<? extends Session> sessions,
      Clock clock) {
    this.practitioners = practitioners;
    this.sessions = sessions;
    this.clock = clock;
  }

  /**
   * Sessions non expirées (la plus récemment utilisée d'abord) et dernière connexion réussie.
   * {@code currentSessionId} est l'identifiant HTTP de la session qui porte l'appel : il sert
   * seulement à poser {@code current}.
   */
  public SecurityOverview overview(UUID practitionerId, String currentSessionId) {
    Instant now = clock.instant();
    List<ActiveSessionRow> rows = practitioners.activeSessions(practitionerId, now);
    Map<String, String> devices = devices(practitionerId, rows);
    List<ActiveSession> active =
        rows.stream()
            .map(
                r ->
                    new ActiveSession(
                        UUID.fromString(r.primaryId()),
                        r.createdAt(),
                        r.lastAccessAt(),
                        r.expiresAt(),
                        r.sessionId().equals(currentSessionId),
                        devices.get(r.sessionId()),
                        null))
            .toList();
    return new SecurityOverview(active, practitioners.lastLogin(practitionerId).orElse(null));
  }

  /**
   * Révoque une autre session du praticien. {@link SessionNotFoundException} si elle est inconnue,
   * expirée ou à un autre compte (même réponse dans les trois cas) ; {@link
   * CurrentSessionException} si c'est la session courante (utiliser la déconnexion).
   */
  public void revoke(UUID practitionerId, UUID id, String currentSessionId, String ip) {
    String primaryId = id.toString();
    ActiveSessionRow target =
        practitioners.activeSessions(practitionerId, clock.instant()).stream()
            .filter(r -> r.primaryId().equals(primaryId))
            .findFirst()
            .orElseThrow(SessionNotFoundException::new);
    if (target.sessionId().equals(currentSessionId)) {
      throw new CurrentSessionException();
    }
    if (practitioners.deleteSession(practitionerId, primaryId) == 0) {
      // Fermée entre la lecture et la suppression (expiration, autre révocation).
      throw new SessionNotFoundException();
    }
    practitioners.insertSecurityEvent(
        practitionerId, "session_revoked", ip, "{\"scope\":\"one\",\"count\":1}", clock.instant());
  }

  /** Libellé d'appareil de chaque session, par identifiant HTTP ; absent si non enregistré. */
  private Map<String, String> devices(UUID practitionerId, List<ActiveSessionRow> rows) {
    Map<String, String> devices = new HashMap<>();
    if (rows.isEmpty()) {
      return devices;
    }
    sessions
        .findByPrincipalName(practitionerId.toString())
        .forEach(
            (sessionId, session) -> {
              Object device = session.getAttribute(UserAgents.SESSION_ATTRIBUTE);
              if (device instanceof String label) {
                devices.put(sessionId, label);
              }
            });
    return devices;
  }
}
