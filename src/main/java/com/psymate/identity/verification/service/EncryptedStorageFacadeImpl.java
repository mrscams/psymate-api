package com.psymate.identity.verification.service;

import com.psymate.identity.api.EncryptedStorageFacade;
import org.springframework.stereotype.Component;

/** Chiffre avec {@link DocumentCipher} (clé de stockage = donnée authentifiée) puis stocke. */
@Component
class EncryptedStorageFacadeImpl implements EncryptedStorageFacade {

  private final DocumentCipher cipher;
  private final StorageService storage;

  EncryptedStorageFacadeImpl(DocumentCipher cipher, StorageService storage) {
    this.cipher = cipher;
    this.storage = storage;
  }

  @Override
  public void put(String key, byte[] plain) {
    check(key);
    storage.put(key, cipher.encrypt(plain, key));
  }

  @Override
  public byte[] get(String key) {
    check(key);
    return cipher.decrypt(storage.get(key), key);
  }

  @Override
  public void delete(String key) {
    check(key);
    storage.delete(key);
  }

  @Override
  public boolean exists(String key) {
    check(key);
    return storage.exists(key);
  }

  @Override
  public String sign(String data) {
    return cipher.sign(data);
  }

  private static void check(String key) {
    if (key == null
        || key.isBlank()
        || key.contains("..")
        || key.startsWith("/")
        || key.startsWith("\\")
        || key.contains("\\")) {
      throw new IllegalArgumentException("clé de stockage invalide");
    }
  }
}
