package com.psymate.identity.auth.service;

import com.psymate.identity.auth.model.ActiveSession;
import com.psymate.identity.auth.model.CurrentSessionException;
import com.psymate.identity.auth.model.LastLogin;
import com.psymate.identity.auth.model.SecurityOverview;
import com.psymate.identity.auth.model.SessionNotFoundException;
import com.psymate.identity.auth.repository.PractitionerRepository;
import com.psymate.identity.repository.SessionNativeQueries.ActiveSessionRow;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Sessions actives et connexion précédente du praticien (M10-06, fiche 18). Les sessions sont
 * celles de Spring Session JDBC, lues en SQL ({@code spring_session.principal_name} porte l'id du
 * praticien) : un praticien ne voit et ne révoque jamais que les siennes. Aucune dépendance au
 * dépôt Spring Session, qui n'existe pas hors application web (profil cli). L'identifiant exposé
 * est la clé interne de la ligne, jamais l'identifiant HTTP que porte le cookie ; aucune adresse IP
 * n'est lue ni rendue (A9 R7) et la ville n'est pas renseignée en V1 (aucune géolocalisation).
 */
@Service
public class SessionSecurityService {

  private final PractitionerRepository practitioners;
  private final Clock clock;

  SessionSecurityService(PractitionerRepository practitioners, Clock clock) {
    this.practitioners = practitioners;
    this.clock = clock;
  }

  /**
   * Sessions non expirées (la plus récemment utilisée d'abord) et connexion précédente : le dernier
   * {@code login_ok} antérieur à l'ouverture de la session courante (null à la première connexion,
   * ou pour une session ouverte avant M10-06). {@code currentSessionId} est l'identifiant HTTP de
   * la session qui porte l'appel : il sert à poser {@code current} et à retrouver son ouverture.
   */
  public SecurityOverview overview(UUID practitionerId, String currentSessionId) {
    List<ActiveSessionRow> rows = practitioners.activeSessions(practitionerId, clock.instant());
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
                        r.device(),
                        null))
            .toList();
    LastLogin previous =
        rows.stream()
            .filter(r -> r.sessionId().equals(currentSessionId) && r.openedAt() != null)
            .findFirst()
            .flatMap(r -> practitioners.lastLoginBefore(practitionerId, r.openedAt()))
            .orElse(null);
    return new SecurityOverview(active, previous);
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
}
