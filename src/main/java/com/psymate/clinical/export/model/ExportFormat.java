package com.psymate.clinical.export.model;

import java.util.Locale;
import java.util.Optional;

/** Formats d'export d'un dossier patient (paramètre {@code format}). */
public enum ExportFormat {
  MD("md", "text/markdown; charset=utf-8"),
  PDF("pdf", "application/pdf");

  private final String code;
  private final String contentType;

  ExportFormat(String code, String contentType) {
    this.code = code;
    this.contentType = contentType;
  }

  public String code() {
    return code;
  }

  public String contentType() {
    return contentType;
  }

  /** {@code md} ou {@code pdf} (casse ignorée) ; vide pour toute autre valeur. */
  public static Optional<ExportFormat> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String v = value.trim().toLowerCase(Locale.ROOT);
    for (ExportFormat f : values()) {
      if (f.code.equals(v)) {
        return Optional.of(f);
      }
    }
    return Optional.empty();
  }
}
