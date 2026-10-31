package com.psymate.identity.verification;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chiffrement applicatif AES-256-GCM des justificatifs (clé {@code app.crypto.key}, 32 octets en
 * base64). Format stocké : IV (12 octets) puis texte chiffré et tag. La clé de stockage sert de
 * donnée authentifiée : un objet recopié sous une autre clé ne se déchiffre pas. La même clé dérive
 * (HMAC) la clé de signature des URL.
 */
@Component
class DocumentCipher {

  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec aesKey;
  private final SecretKeySpec macKey;
  private final SecureRandom random = new SecureRandom();

  DocumentCipher(@Value("${app.crypto.key}") String base64Key) {
    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(base64Key.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("app.crypto.key doit être une clé de 32 octets en base64", e);
    }
    if (raw.length != 32) {
      throw new IllegalStateException("app.crypto.key doit faire 32 octets (AES-256)");
    }
    this.aesKey = new SecretKeySpec(raw, "AES");
    this.macKey = new SecretKeySpec(hmac(raw, "psymate-signed-url"), "HmacSHA256");
  }

  byte[] encrypt(byte[] plain, String aad) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
      c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
      byte[] ct = c.doFinal(plain);
      return ByteBuffer.allocate(IV_BYTES + ct.length).put(iv).put(ct).array();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  byte[] decrypt(byte[] stored, String aad) {
    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
      c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
      return c.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("justificatif illisible (clé ou contenu invalide)", e);
    }
  }

  /** Signature HMAC-SHA256 en base64url, pour les URL signées. */
  String sign(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(macKey);
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] hmac(byte[] key, String label) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
