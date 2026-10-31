package com.psymate.identity.admin;

import java.util.regex.Pattern;

/** Adresse IP d'une requête d'administration, nettoyée avant d'être consignée au journal. */
final class AdminIps {

  private static final Pattern IP = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

  private AdminIps() {}

  /** Adresse sans zone IPv6, ou null si elle est absente ou illisible. */
  static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    int zone = s.indexOf('%');
    if (zone >= 0) {
      s = s.substring(0, zone);
    }
    return IP.matcher(s).matches() && (s.contains(".") || s.contains(":")) ? s : null;
  }
}
