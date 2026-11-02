package com.psymate.identity.auth.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chiffrement AES-256-GCM du secret TOTP (colonne totp_secret_enc) avec la clé {@code
 * app.crypto.key}. Format : IV (12 octets) puis texte chiffré et tag ; l'identifiant du praticien
 * sert de donnée authentifiée. Même schéma que verification.DocumentCipher (package-private dans un
 * autre sous-package) : une factorisation dans un composant commun est souhaitable.
 */
@Component
class TotpSecretCipher {

  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  TotpSecretCipher(@Value("${app.crypto.key}") String base64Key) {
    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(base64Key.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("app.crypto.key doit être une clé de 32 octets en base64", e);
    }
    if (raw.length != 32) {
      throw new IllegalStateException("app.crypto.key doit faire 32 octets (AES-256)");
    }
    this.key = new SecretKeySpec(raw, "AES");
  }

  byte[] encrypt(byte[] secret, UUID practitionerId) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      c.updateAAD(aad(practitionerId));
      byte[] ct = c.doFinal(secret);
      return ByteBuffer.allocate(IV_BYTES + ct.length).put(iv).put(ct).array();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  byte[] decrypt(byte[] stored, UUID practitionerId) {
    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
      c.updateAAD(aad(practitionerId));
      return c.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("secret TOTP illisible (clé ou contenu invalide)", e);
    }
  }

  private static byte[] aad(UUID id) {
    return ("totp:" + id).getBytes(StandardCharsets.UTF_8);
  }
}
