package com.psymate.identity.auth.model;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP (RFC 6238) en Java standard : HMAC-SHA1, pas de 30 s, 6 chiffres, secret de 160 bits encodé
 * en Base32 (RFC 4648, sans remplissage) pour les applications d'authentification.
 */
public final class Totp {

  static final int STEP_SECONDS = 30;
  static final int DIGITS = 6;
  static final int SECRET_BYTES = 20;

  /** Tolérance de dérive d'horloge : un pas avant et un pas après. */
  static final int DRIFT_STEPS = 1;

  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final SecureRandom RANDOM = new SecureRandom();

  private Totp() {}

  public static byte[] newSecret() {
    byte[] secret = new byte[SECRET_BYTES];
    RANDOM.nextBytes(secret);
    return secret;
  }

  static long stepAt(long epochSeconds) {
    return Math.floorDiv(epochSeconds, STEP_SECONDS);
  }

  /** Code à 6 chiffres du pas donné (RFC 4226, troncature dynamique). */
  static String code(byte[] secret, long step) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secret, "HmacSHA1"));
      byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
      int offset = h[h.length - 1] & 0x0f;
      int binary =
          ((h[offset] & 0x7f) << 24)
              | ((h[offset + 1] & 0xff) << 16)
              | ((h[offset + 2] & 0xff) << 8)
              | (h[offset + 3] & 0xff);
      return String.format("%06d", binary % 1_000_000);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Pas correspondant au code (dans la tolérance de dérive), ou -1 si le code est faux. */
  public static long match(byte[] secret, String candidate, long epochSeconds) {
    if (candidate == null || !candidate.matches("[0-9]{6}")) {
      return -1;
    }
    long current = stepAt(epochSeconds);
    long found = -1;
    byte[] given = candidate.getBytes(StandardCharsets.US_ASCII);
    for (long s = current - DRIFT_STEPS; s <= current + DRIFT_STEPS; s++) {
      byte[] expected = code(secret, s).getBytes(StandardCharsets.US_ASCII);
      if (MessageDigest.isEqual(expected, given)) {
        found = s;
      }
    }
    return found;
  }

  public static String base32(byte[] data) {
    StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
    int buffer = 0;
    int bits = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xff);
      bits += 8;
      while (bits >= 5) {
        sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1f));
        bits -= 5;
      }
      buffer &= (1 << bits) - 1;
    }
    if (bits > 0) {
      sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
    }
    return sb.toString();
  }

  static byte[] fromBase32(String text) {
    String s = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
    byte[] out = new byte[s.length() * 5 / 8];
    int buffer = 0;
    int bits = 0;
    int index = 0;
    for (char c : s.toCharArray()) {
      int v = ALPHABET.indexOf(c);
      if (v < 0) {
        throw new IllegalArgumentException("not base32");
      }
      buffer = (buffer << 5) | v;
      bits += 5;
      if (bits >= 8) {
        out[index++] = (byte) ((buffer >> (bits - 8)) & 0xff);
        bits -= 8;
        buffer &= (1 << bits) - 1;
      }
    }
    return out;
  }

  /** URI otpauth:// lue par les applications d'authentification (QR code). */
  public static String otpauthUri(String issuer, String account, byte[] secret) {
    return "otpauth://totp/"
        + enc(issuer)
        + ":"
        + enc(account)
        + "?secret="
        + base32(secret)
        + "&issuer="
        + enc(issuer)
        + "&algorithm=SHA1&digits="
        + DIGITS
        + "&period="
        + STEP_SECONDS;
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
