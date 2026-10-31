package com.psymate.directory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stockage sur disque local (dev). Un backend objet le remplacera en production. */
@Component
class LocalPhotoStorage implements PhotoStorage {

  private final Path root;

  LocalPhotoStorage(@Value("${app.directory.storage-dir}") String dir) {
    this.root = Path.of(dir).toAbsolutePath().normalize();
  }

  @Override
  public String store(UUID practitionerId, byte[] jpeg) {
    String key = "profiles/" + practitionerId + "/" + UUID.randomUUID() + ".jpg";
    try {
      Path target = resolve(key);
      Files.createDirectories(target.getParent());
      Files.write(target, jpeg);
      return key;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public byte[] read(String key) {
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

  private Path resolve(String key) {
    Path p = root.resolve(key).normalize();
    if (!p.startsWith(root)) {
      throw new IllegalArgumentException("clé de stockage invalide");
    }
    return p;
  }
}
