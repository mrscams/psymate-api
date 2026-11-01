package com.psymate.identity.persistence;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Conversion d'une adresse IP littérale (colonnes {@code inet}) en {@link InetAddress}. Une valeur
 * qui n'est pas un littéral IPv4 ou IPv6 donne null : aucune résolution DNS n'est jamais tentée.
 */
public final class IpLiterals {

  private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
  private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]+$");

  private IpLiterals() {}

  public static InetAddress parse(String raw) {
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
      return InetAddress.getByName(s);
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
