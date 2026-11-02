package com.psymate.identity.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Chemin d'une requête tel que Spring MVC le route, c'est-à-dire décodé : {@code /api/%61dmin/x}
 * est bien {@code /api/admin/x}. Les filtres par chemin ne doivent jamais se fier à {@code
 * getRequestURI()}, qui est brut : un encodage pour cent suffirait à les contourner (M1-14b).
 */
final class RequestPaths {

  private RequestPaths() {}

  /** Chemin décodé dans l'application (sans le contexte). */
  static String decoded(HttpServletRequest request) {
    String servletPath = request.getServletPath();
    String pathInfo = request.getPathInfo();
    String path = (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
    if (path.isEmpty()) {
      String raw = request.getRequestURI().substring(request.getContextPath().length());
      path = URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
    return path;
  }
}
