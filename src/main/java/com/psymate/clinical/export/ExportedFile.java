package com.psymate.clinical.export;

/** Fichier d'export prêt à être servi : nom, type de contenu et octets. */
public record ExportedFile(String filename, String contentType, byte[] content) {}
