package com.psymate.identity.admin;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Générateur TOTP de test (RFC 6238, HMAC-SHA1, 6 chiffres, pas de 30 s), indépendant du code de
 * production.
 */
public final class AdminTotp {

  private AdminTotp() {}

  private static byte[] base32Decode(String s) {
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int buffer = 0;
    int bits = 0;
    for (char c : s.toCharArray()) {
      buffer = (buffer << 5) | alphabet.indexOf(c);
      bits += 5;
      if (bits >= 8) {
        out.write((buffer >> (bits - 8)) & 0xff);
        bits -= 8;
        buffer &= (1 << bits) - 1;
      }
    }
    return out.toByteArray();
  }

  public static String code(String base32Secret, Instant at) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(base32Decode(base32Secret), "HmacSHA1"));
      byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(at.getEpochSecond() / 30).array());
      int o = h[19] & 0xf;
      int bin =
          ((h[o] & 0x7f) << 24)
              | ((h[o + 1] & 0xff) << 16)
              | ((h[o + 2] & 0xff) << 8)
              | (h[o + 3] & 0xff);
      return String.format("%06d", bin % 1_000_000);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
