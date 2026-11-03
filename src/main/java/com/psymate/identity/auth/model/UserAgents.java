package com.psymate.identity.auth.model;

/**
 * Appareil déduit d'un en-tête {@code User-Agent} (M10-06) : navigateur et système, jamais la
 * chaîne brute. Seuls des libellés d'une liste fermée sont rendus, ce qui permet de les stocker
 * (attribut de session, détail de l'événement {@code login_ok}) sans rien échapper.
 */
public final class UserAgents {

  /** Au-delà, l'en-tête est tronqué avant analyse (un User-Agent réel fait moins de 300). */
  private static final int MAX_LENGTH = 1_000;

  private UserAgents() {}

  /**
   * Libellé « Chrome · Windows », ou le navigateur ou le système seul, ou null si rien n'est
   * reconnu.
   */
  public static String device(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) {
      return null;
    }
    String ua = userAgent.length() > MAX_LENGTH ? userAgent.substring(0, MAX_LENGTH) : userAgent;
    String browser = browser(ua);
    String os = os(ua);
    if (browser != null && os != null) {
      return browser + " · " + os;
    }
    return browser != null ? browser : os;
  }

  // Ordre significatif : Edge, Opera et Chrome pour iOS portent aussi « Chrome » ou « Safari ».
  private static String browser(String ua) {
    if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) {
      return "Edge";
    }
    if (ua.contains("OPR/") || ua.contains("Opera")) {
      return "Opera";
    }
    if (ua.contains("SamsungBrowser/")) {
      return "Samsung Internet";
    }
    if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
      return "Firefox";
    }
    if (ua.contains("Chrome/") || ua.contains("CriOS/")) {
      return "Chrome";
    }
    if (ua.contains("Safari/") && ua.contains("Version/")) {
      return "Safari";
    }
    return null;
  }

  // iOS avant macOS (« like Mac OS X »), Android avant Linux.
  private static String os(String ua) {
    if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
      return "iOS";
    }
    if (ua.contains("Android")) {
      return "Android";
    }
    if (ua.contains("Windows")) {
      return "Windows";
    }
    if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
      return "macOS";
    }
    if (ua.contains("CrOS")) {
      return "ChromeOS";
    }
    if (ua.contains("Linux") || ua.contains("X11")) {
      return "Linux";
    }
    return null;
  }
}
