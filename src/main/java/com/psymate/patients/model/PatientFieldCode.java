package com.psymate.patients.model;

/**
 * Codes de champ des {@code errors[]} des 422 du module patients (champ {@code code} de {@link
 * com.psymate.shared.problem.FieldProblem}). Ce sont les codes des autres modules (clinical, identity) :
 * REQUIRED (obligatoire manquant ou vide), TOO_LONG (trop long), INVALID_VALUE (hors liste admise),
 * INVALID_DATE (date hors bornes), INVALID_FORMAT (valeur illisible : date, curseur, objet
 * attendu), UNKNOWN_FIELD (clé non modifiable ou inconnue). TAG_IS_EMOTION et TAG_UNKNOWN sont les
 * codes métier de tag, repris tels quels dans errors[0] comme à la racine.
 */
public final class PatientFieldCode {

  public static final String REQUIRED = "REQUIRED";
  public static final String TOO_LONG = "TOO_LONG";
  public static final String INVALID_VALUE = "INVALID_VALUE";
  public static final String INVALID_DATE = "INVALID_DATE";
  public static final String INVALID_FORMAT = "INVALID_FORMAT";
  public static final String UNKNOWN_FIELD = "UNKNOWN_FIELD";
  public static final String TAG_IS_EMOTION = "TAG_IS_EMOTION";
  public static final String TAG_UNKNOWN = "TAG_UNKNOWN";

  private PatientFieldCode() {}
}
