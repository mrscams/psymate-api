package com.psymate.clinical.export.model;

/** Fichier d'export prêt à être servi : nom, type de contenu et octets. */
public record ExportedFile(String filename, String contentType, byte[] content) {}
