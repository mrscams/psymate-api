package com.psymate.identity.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/** Normalisation des adresses IP avant insertion dans une colonne inet. */
final class IpAddresses {

  private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
  private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]+$");

  private IpAddresses() {}

  /**
   * Adresse littérale IPv4 ou IPv6 sans zone ({@code fe80::1%eth0} devient {@code fe80::1}), ou
   * null si la valeur n'est pas une adresse littérale. Aucune résolution DNS.
   */
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

  /** Littéral seulement : une chaîne qui pourrait déclencher une résolution DNS est rejetée. */
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
