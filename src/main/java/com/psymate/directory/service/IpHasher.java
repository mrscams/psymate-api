package com.psymate.directory.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Empreinte technique de l'IP d'un expéditeur (E3.10) : {@code hex(sha256(ip + sel du jour))}. Le
 * sel du jour est dérivé de {@code app.crypto.key} et de la date UTC (HMAC-SHA256) : il change
 * chaque jour, la même IP n'est donc pas corrélable d'un jour à l'autre, et aucun secret
 * supplémentaire n'est nécessaire.
 */
@Component
class IpHasher {

  private final byte[] key;

  IpHasher(@Value("${app.crypto.key}") String base64Key) {
    try {
      this.key = Base64.getDecoder().decode(base64Key);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("app.crypto.key doit être une clé de 32 octets en base64", e);
    }
    if (key.length != 32) {
      throw new IllegalStateException("app.crypto.key doit faire 32 octets");
    }
  }

  /**
   * {@code normalizedIp} : adresse déjà normalisée (IPv6 en /64, voir {@link
   * com.psymate.shared.web.IpAddresses#subnetKey}).
   */
  String hash(String normalizedIp, LocalDate dayUtc) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      String salt =
          HexFormat.of()
              .formatHex(
                  mac.doFinal(("contact-ip-salt:" + dayUtc).getBytes(StandardCharsets.UTF_8)));
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((normalizedIp + salt).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
