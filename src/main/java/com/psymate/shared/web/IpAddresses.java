package com.psymate.shared.web;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Adresses IP des requêtes (ARCH-18) : normalisation avant une colonne {@code inet}, conversion en
 * {@link InetAddress}, clé de regroupement /64. L'adresse est celle que donne {@code
 * getRemoteAddr()} : aucun en-tête {@code X-Forwarded-For} n'est jamais lu ici (derrière un proxy,
 * le déploiement active {@code server.forward-headers-strategy}). Aucune résolution DNS n'est
 * jamais tentée : une valeur qui n'est pas un littéral IPv4 ou IPv6 est rejetée avant {@link
 * InetAddress#getByName}.
 */
public final class IpAddresses {

  private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

  /** 45 caractères : la plus longue forme textuelle d'une IPv6 (avec IPv4 finale). */
  private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

  private IpAddresses() {}

  /**
   * Adresse littérale IPv4 ou IPv6 sans zone ({@code fe80::1%eth0} devient {@code fe80::1}), telle
   * qu'écrite (espaces retirés), ou null si la valeur est absente ou n'est pas une adresse
   * littérale : une valeur douteuse devient null plutôt que de faire échouer le cast {@code inet}.
   */
  public static String normalize(String raw) {
    InetAddress parsed = parse(raw);
    return parsed == null ? null : literal(raw);
  }

  /** Même règle que {@link #normalize}, rendue en {@link InetAddress} (colonnes inet en JPA). */
  public static InetAddress parse(String raw) {
    String s = literal(raw);
    if (s == null || !isLiteral(s)) {
      return null;
    }
    try {
      return InetAddress.getByName(s);
    } catch (UnknownHostException e) {
      return null;
    }
  }

  /**
   * Clé d'un demandeur pour la limitation de débit et l'empreinte des demandes de contact : une
   * IPv6 est regroupée par préfixe /64 (une box en reçoit un entier), une IPv4 reste telle quelle,
   * une valeur absente devient {@code unknown}.
   */
  public static String subnetKey(String remoteAddr) {
    if (remoteAddr == null) {
      return "unknown";
    }
    if (remoteAddr.indexOf(':') >= 0) {
      try {
        // Adresse littérale : aucune résolution DNS.
        InetAddress a = InetAddress.getByName(remoteAddr);
        if (a instanceof Inet6Address) {
          return HexFormat.of().formatHex(a.getAddress(), 0, 8) + "/64";
        }
        return a.getHostAddress();
      } catch (UnknownHostException e) {
        return remoteAddr;
      }
    }
    return remoteAddr;
  }

  /** Valeur sans espaces autour ni zone IPv6, null si absente. */
  private static String literal(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    int zone = s.indexOf('%');
    return zone >= 0 ? s.substring(0, zone) : s;
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
