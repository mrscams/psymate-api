package com.psymate.identity.auth;

import com.psymate.identity.api.CurrentPractitioner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Un administrateur sans 2FA active reçoit 403 TWO_FACTOR_REQUIRED sur /api/admin/** (A10, F33).
 * Les routes elles-mêmes arrivent avec M1-14 ; le contrôle d'appartenance au rôle y sera ajouté.
 */
class AdminTwoFactorFilter extends OncePerRequestFilter {

  private final TotpRepository totp;

  AdminTwoFactorFilter(TotpRepository totp) {
    this.totp = totp;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !(path.equals("/api/admin") || path.startsWith("/api/admin/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<CurrentPractitioner> current = CurrentPractitioner.get();
    boolean admin =
        auth != null
            && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    if (admin && current.isPresent() && !totp.find(current.get().id()).enabled()) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response
          .getWriter()
          .write(
              "{\"type\":\"about:blank\",\"title\":\"Double authentification requise\","
                  + "\"status\":403,\"code\":\"TWO_FACTOR_REQUIRED\","
                  + "\"detail\":\"Activez la double authentification pour accéder à "
                  + "l'administration.\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
