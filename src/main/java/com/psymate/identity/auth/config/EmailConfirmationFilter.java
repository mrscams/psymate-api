package com.psymate.identity.auth.config;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.auth.model.PractitionerRow;
import com.psymate.identity.auth.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Un compte email_pending reçoit 403 EMAIL_NOT_CONFIRMED sur tout /api/** hors /api/auth/** (fiche
 * 25). Seul ce statut est refusé : tout autre statut du principal passe. Si l'e-mail a été confirmé
 * depuis un autre navigateur, la session est rafraîchie au lieu d'être refusée.
 */
public class EmailConfirmationFilter extends OncePerRequestFilter {

  public static final String PENDING = "email_pending";

  private final AuthService authService;
  private final SecurityContextRepository contextRepository;

  EmailConfirmationFilter(AuthService authService, SecurityContextRepository contextRepository) {
    this.authService = authService;
    this.contextRepository = contextRepository;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = RequestPaths.decoded(request);
    return !path.startsWith("/api/") || path.startsWith("/api/auth/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<CurrentPractitioner> current = CurrentPractitioner.get();
    if (current.isPresent() && PENDING.equals(current.get().verificationStatus())) {
      Optional<PractitionerRow> fresh = authService.find(current.get().id());
      if (fresh.isPresent() && !PENDING.equals(fresh.get().verificationStatus())) {
        refresh(fresh.get(), contextRepository, request, response);
      } else {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response
            .getWriter()
            .write(
                "{\"type\":\"about:blank\",\"title\":\"E-mail non confirmé\","
                    + "\"status\":403,\"code\":\"EMAIL_NOT_CONFIRMED\","
                    + "\"detail\":\"Confirmez votre adresse e-mail pour accéder à cette "
                    + "ressource.\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }

  /** Remplace le principal de la session par l'état courant du compte. */
  public static void refresh(
      PractitionerRow row,
      SecurityContextRepository repository,
      HttpServletRequest request,
      HttpServletResponse response) {
    Authentication old = SecurityContextHolder.getContext().getAuthentication();
    CurrentPractitioner updated =
        new CurrentPractitioner(
            row.id(),
            row.tenantId(),
            row.email(),
            row.displayName(),
            row.profession(),
            row.verificationStatus());
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            updated, null, old == null ? java.util.List.of() : old.getAuthorities());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    repository.saveContext(context, request, response);
  }
}
