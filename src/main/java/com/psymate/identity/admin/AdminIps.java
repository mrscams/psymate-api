package com.psymate.identity.admin;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Adresse IP d'une requête d'administration, nettoyée avant d'être consignée au journal (colonne
 * inet). Même règle que identity.auth.IpAddresses : littéral IPv4 ou IPv6 seulement, sans zone,
 * jamais de résolution DNS ; toute valeur douteuse devient null plutôt que de faire échouer le cast
 * inet (et donc l'acte d'administration).
 */
final class AdminIps {

  private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
  private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

  private AdminIps() {}

  /** Adresse littérale sans zone IPv6, ou null si elle est absente ou illisible. */
  static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    int zone = s.indexOf('%');
    if (zone >= 0) {
      s = s.substring(0, zone);
    }
    if (!isLiteral(s)) {
      return null;
    }
    try {
      InetAddress.getByName(s);
      return s;
    } catch (UnknownHostException e) {
      return null;
    }
  }

  private static boolean isLiteral(String s) {
    if (IPV4.matcher(s).matches()) {
      for (String part : s.split("[.]")) {
        if (Integer.parseInt(part) > 255) {
          return false;
        }
      }
      return true;
    }
    return s.contains(":") && IPV6.matcher(s).matches();
  }
}
