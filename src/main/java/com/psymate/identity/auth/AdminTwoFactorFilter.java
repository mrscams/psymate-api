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
 * Garde de /api/admin/** (A10, F33). Le contrôle du rôle est ajouté par M1-14 et passe avant celui
 * de la 2FA : sans l'autorité ROLE_ADMIN, 403 ADMIN_ROLE_REQUIRED ; un administrateur sans 2FA
 * active reçoit 403 TWO_FACTOR_REQUIRED.
 */
class AdminTwoFactorFilter extends OncePerRequestFilter {

  private final TotpRepository totp;

  AdminTwoFactorFilter(TotpRepository totp) {
    this.totp = totp;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = RequestPaths.decoded(request);
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
    if (!admin) {
      reject(
          response,
          "Accès réservé à l'administration",
          "ADMIN_ROLE_REQUIRED",
          "Cette ressource est réservée aux administrateurs.");
      return;
    }
    // Fermé par défaut : sans principal CurrentPractitioner identifiable, la 2FA est réputée
    // absente.
    if (current.isEmpty() || !totp.find(current.get().id()).enabled()) {
      reject(
          response,
          "Double authentification requise",
          "TWO_FACTOR_REQUIRED",
          "Activez la double authentification pour accéder à l'administration.");
      return;
    }
    chain.doFilter(request, response);
  }

  private static void reject(HttpServletResponse response, String title, String code, String detail)
      throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + title
                + "\",\"status\":403,\"code\":\""
                + code
                + "\",\"detail\":\""
                + detail
                + "\"}");
  }
}
