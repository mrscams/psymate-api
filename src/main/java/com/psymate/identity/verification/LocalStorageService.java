package com.psymate.identity.verification;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Disque local sous {@code app.storage.dir} (dev). */
@Component
class LocalStorageService implements StorageService {

  private final Path root;

  LocalStorageService(@Value("${app.storage.dir}") String dir) {
    this.root = Path.of(dir).toAbsolutePath().normalize();
  }

  @Override
  public void put(String key, byte[] data) {
    try {
      Path target = resolve(key);
      Files.createDirectories(target.getParent());
      Files.write(target, data);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public byte[] get(String key) {
    try {
      return Files.readAllBytes(resolve(key));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public boolean exists(String key) {
    return Files.exists(resolve(key));
  }

  private Path resolve(String key) {
    Path p = root.resolve(key).normalize();
    if (!p.startsWith(root)) {
      throw new IllegalArgumentException("clé de stockage invalide");
    }
    return p;
  }
}
