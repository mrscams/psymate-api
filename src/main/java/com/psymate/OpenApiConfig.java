package com.psymate;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Métadonnées et conventions transverses du contrat OpenAPI publié (contract/openapi.yaml, M7-09).
 * Tout ce qui est commun à toutes les opérations est décrit ici une seule fois : description
 * générale, schéma {@code ProblemDetail} des erreurs RFC 9457, réponses 401 et 429 (et 403 des
 * filtres) ajoutées automatiquement selon le chemin, ordre des tags. Le détail propre à chaque
 * opération reste dans les annotations springdoc du contrôleur.
 */
@Configuration
class OpenApiConfig {

  /** Nom du schéma commun des erreurs, référencé par chaque réponse 4xx/5xx. */
  static final String PROBLEM_SCHEMA = "ProblemDetail";

  static final String FIELD_PROBLEM_SCHEMA = "FieldProblem";

  /**
   * Routes servies sans session (voir identity.auth.SecurityConfig et
   * directory.PublicSecurityConfig) : aucune 401 UNAUTHENTICATED transverse n'y est ajoutée.
   */
  private static final List<String> PUBLIC_PREFIXES =
      List.of(
          "/api/auth/register",
          "/api/auth/signup",
          "/api/auth/email/confirm",
          "/api/auth/email/resend",
          "/api/auth/email/change",
          "/api/auth/login",
          "/api/auth/password/change",
          "/api/auth/logout",
          "/api/auth/password-reset/",
          "/api/me/appeal",
          "/api/me/export",
          "/api/public/");

  /** Ordre de présentation des tags (un tag par contrôleur). */
  private static final List<String> TAG_ORDER =
      List.of(
          "Authentification",
          "Double authentification",
          "Préférences",
          "Vérification du praticien",
          "Patients",
          "Séances",
          "Entrées",
          "Chronologie",
          "Liens",
          "Graphe",
          "Vue d'ensemble",
          "Recherche",
          "Vocabulaire",
          "Tags",
          "Émotions personnelles",
          "Export patient",
          "Export de compte",
          "Recours",
          "Profil annuaire",
          "Vocabulaire de l'annuaire",
          "Annuaire public",
          "Contact public",
          "Administration : vérifications",
          "Administration : praticiens",
          "Administration : journal",
          "Administration : vocabulaire de l'annuaire");

  private static final String DESCRIPTION =
      """
      API de PsyMate, suivi clinique pour psychologues, psychiatres et coachs. Ce document est \
      la seule référence nécessaire pour écrire un client : chaque opération décrit qui peut \
      l'appeler, ce qu'elle attend, ce qu'elle renvoie et chaque erreur à gérer.

      **Authentification.** Session serveur portée par un cookie HttpOnly, SameSite=Lax, posé par \
      `POST /api/auth/login` (12 h, 30 j avec rememberMe) ; aucun jeton à envoyer. Sans session \
      valide, toute route hors `/api/auth/*` et `/api/public/**` répond 401 UNAUTHENTICATED. Un \
      compte dont l'e-mail n'est pas confirmé reçoit 403 EMAIL_NOT_CONFIRMED hors `/api/auth/*`. \
      Les routes `/api/admin/**` exigent le rôle admin (403 ADMIN_ROLE_REQUIRED) et la double \
      authentification active (403 TWO_FACTOR_REQUIRED). Un compte suspendu n'a pas de session : \
      `POST /api/me/appeal` et `/api/me/export` l'identifient par `{email, password, otp?}` dans le \
      corps (202 `{otpRequired: true}` quand le code 2FA manque).

      **Erreurs.** Toujours `application/problem+json` (RFC 9457), schéma `ProblemDetail` : `type`, \
      `title`, `status`, `detail`, `instance`, plus `code` (identifiant stable, à tester en \
      priorité) et, pour les 422, `errors[]` `{field, code, message}` par champ. Codes transverses \
      : 400 MALFORMED_JSON (JSON illisible ; les modules clinical, linking et admin répondent \
      MALFORMED_REQUEST pour un corps absent ou illisible) ou BAD_REQUEST (paramètre manquant ou mal typé, \
      `errors[]` avec INVALID_FIELD), 401 UNAUTHENTICATED, 403 FORBIDDEN / EMAIL_NOT_CONFIRMED / \
      ADMIN_ROLE_REQUIRED / TWO_FACTOR_REQUIRED, 404 NOT_FOUND (route inconnue), 405 \
      METHOD_NOT_ALLOWED, 406 NOT_ACCEPTABLE, 413 UPLOAD_TOO_LARGE (plafond multipart global), 415 \
      UNSUPPORTED_MEDIA_TYPE, 422 VALIDATION_FAILED, 429 RATE_LIMITED, 500 INTERNAL_ERROR (détail \
      générique, jamais le message interne). Les codes métier (PATIENT_ARCHIVED, LINK_EXISTS, \
      SESSION_REQUIRED…) sont listés sur chaque opération. Les textes `title`, `detail` et \
      `message` suivent `Accept-Language` (français par défaut, anglais).

      **Limites de débit** (G Q24) : 600 requêtes/min par session sur `/api/**`, 60/min par IP \
      sur `/api/public/**`, 5/h par IP sur l'envoi de contact. Au-delà : 429 RATE_LIMITED avec \
      l'en-tête `Retry-After` (secondes) et le champ `retryAfterSeconds` ; les 423 d'authentification \
      (verrou après échecs) portent les mêmes.

      **Formats.** JSON camelCase ; dates-heures en UTC ISO 8601 (`2026-03-14T09:30:00Z`), dates \
      civiles `AAAA-MM-JJ` ; identifiants UUID. Les listes sont paginées par curseur opaque \
      (`cursor`, `limit`, réponse `{items[], nextCursor}`, `nextCursor` null en fin), sauf \
      l'annuaire public et la recherche (`page`, 20 résultats par page). Les champs en lecture \
      seule (`authorType`, `submissionId`) sont refusés en écriture (422 UNKNOWN_FIELD). Rien n'est \
      supprimé physiquement : archivage et versions. Les fichiers sortent avec un \
      `Content-Disposition` (`attachment` pour les exports, `inline` pour les justificatifs) ; les \
      réponses publiques portent un `Cache-Control` explicite.

      **Version du contrat** : `info.version` suit le plan D6-A4, un tag `contract-vX.Y.Z` est posé \
      à chaque bump. Les tags regroupent les opérations par contrôleur, dans l'ordre d'usage : \
      identité, dossier clinique, exploration, annuaire, administration.""";

  @Bean
  OpenAPI psymateOpenApi(@Value("${app.contract.version}") String contractVersion) {
    return new OpenAPI()
        // Serveur relatif : le client genere vise l'origine qui sert l'API, aucun hote en dur.
        .servers(List.of(new Server().url("/").description("Serveur courant")))
        .info(new Info().title("PsyMate API").version(contractVersion).description(DESCRIPTION));
  }

  /**
   * Après génération : remplace le schéma {@code ProblemDetail} de Spring (sans {@code code} ni
   * {@code errors}) par le schéma complet des erreurs PsyMate, ajoute les réponses transverses
   * produites par les filtres (401, 403 des filtres, 429) à chaque opération qui ne les déclare
   * pas, et ordonne les tags.
   */
  @Bean
  OpenApiCustomizer psymateContractCustomizer() {
    return openApi -> {
      openApi.getComponents().addSchemas(FIELD_PROBLEM_SCHEMA, fieldProblemSchema());
      openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
      if (openApi.getPaths() != null) {
        openApi.getPaths().forEach(OpenApiConfig::addTransverseResponses);
      }
      orderTags(openApi);
    };
  }

  private static void addTransverseResponses(String path, PathItem item) {
    boolean isPublic = PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    boolean isAuth = path.startsWith("/api/auth/");
    boolean isAdmin = path.startsWith("/api/admin/");
    for (Operation op : item.readOperations()) {
      ApiResponses responses = op.getResponses();
      if (responses == null) {
        responses = new ApiResponses();
        op.setResponses(responses);
      }
      if (!isPublic) {
        putIfAbsent(
            responses,
            "401",
            "UNAUTHENTICATED : aucune session valide (cookie absent, expiré ou révoqué).");
      }
      if (!isPublic && !isAuth) {
        merge(responses, "403", "EMAIL_NOT_CONFIRMED : adresse e-mail du compte non confirmée.");
      }
      if (isAdmin) {
        merge(
            responses,
            "403",
            "ADMIN_ROLE_REQUIRED : compte sans rôle admin ; TWO_FACTOR_REQUIRED : double"
                + " authentification non activée sur le compte admin.");
      }
      merge(
          responses,
          "429",
          "RATE_LIMITED : limite de débit atteinte (en-tête Retry-After et champ"
              + " retryAfterSeconds, en secondes).");
    }
  }

  private static void putIfAbsent(ApiResponses responses, String status, String description) {
    if (!responses.containsKey(status)) {
      responses.addApiResponse(status, problemResponse(description));
    }
  }

  /** Ajoute la réponse, ou complète la description d'une réponse déjà déclarée par l'opération. */
  private static void merge(ApiResponses responses, String status, String description) {
    ApiResponse existing = responses.get(status);
    if (existing == null) {
      responses.addApiResponse(status, problemResponse(description));
      return;
    }
    String current = existing.getDescription() == null ? "" : existing.getDescription().trim();
    String code = description.substring(0, description.indexOf(' '));
    if (!current.contains(code)) {
      existing.setDescription(current.isEmpty() ? description : current + " " + description);
    }
    if (existing.getContent() == null || existing.getContent().isEmpty()) {
      existing.setContent(problemContent());
    }
  }

  private static ApiResponse problemResponse(String description) {
    return new ApiResponse().description(description).content(problemContent());
  }

  private static Content problemContent() {
    return new Content()
        .addMediaType(
            org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)));
  }

  @SuppressWarnings("rawtypes")
  private static Schema fieldProblemSchema() {
    Map<String, Schema> props = new LinkedHashMap<>();
    props.put(
        "field",
        new StringSchema()
            .description(
                "Chemin du champ fautif dans le corps envoyé (notation pointée pour les objets"
                    + " imbriqués, ex. feeling.emotionCode) ; chaîne vide si l'erreur ne vise"
                    + " aucun champ précis.")
            .example("occurred"));
    props.put(
        "code",
        new StringSchema()
            .description(
                "Code stable de l'erreur de champ, à tester par le client : INVALID_FIELD,"
                    + " REQUIRED, OUT_OF_RANGE, TOO_LONG, UNKNOWN_EMOTION… (liste sur chaque"
                    + " opération). Absent dans les modules patients, vocabulary (tags, émotions)"
                    + " et directory (profil, annuaire public, contact), dont les erreurs de champ"
                    + " ne portent que field et message.")
            .example("REQUIRED"));
    props.put(
        "message",
        new StringSchema()
            .description("Message lisible, dans la langue d'Accept-Language (français par défaut).")
            .example("La valeur est obligatoire."));
    return new ObjectSchema()
        .description(
            "Une erreur de champ dans errors[] d'une réponse 422 VALIDATION_FAILED (ou 400"
                + " BAD_REQUEST / INVALID_PARAMETERS pour un paramètre de requête).")
        .properties(props)
        .required(List.of("field", "message"));
  }

  @SuppressWarnings("rawtypes")
  private static Schema problemSchema() {
    Map<String, Schema> props = new LinkedHashMap<>();
    props.put(
        "type",
        new StringSchema()
            .format("uri")
            .description("Type de problème (RFC 9457) ; toujours about:blank dans PsyMate.")
            .example("about:blank"));
    props.put(
        "title",
        new StringSchema()
            .description(
                "Titre court du problème, libellé HTTP ou titre métier, dans la langue"
                    + " d'Accept-Language.")
            .example("Unprocessable Content"));
    props.put(
        "status",
        new IntegerSchema()
            .description("Statut HTTP de la réponse, répété dans le corps.")
            .example(422));
    props.put(
        "detail",
        new StringSchema()
            .description(
                "Explication lisible destinée à l'utilisateur ; générique pour les erreurs 5xx"
                    + " (jamais de message interne).")
            .example("Certains champs sont invalides."));
    props.put(
        "instance",
        new StringSchema()
            .format("uri")
            .description("Chemin de la requête en erreur ; absent pour les erreurs des filtres.")
            .example("/api/patients/9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e/entries"));
    props.put(
        "code",
        new StringSchema()
            .description(
                "Code d'erreur stable, en MAJUSCULES_SOULIGNÉES, indépendant de la langue : c'est"
                    + " lui que le client teste. Codes transverses dans la description générale,"
                    + " codes métier sur chaque opération.")
            .example("VALIDATION_FAILED"));
    props.put(
        "errors",
        new ArraySchema()
            .items(new Schema<>().$ref("#/components/schemas/" + FIELD_PROBLEM_SCHEMA))
            .description(
                "Erreurs par champ ; présent sur les 422 VALIDATION_FAILED et les 400 BAD_REQUEST"
                    + " de paramètre, absent sinon."));
    props.put(
        "retryAfterSeconds",
        new IntegerSchema()
            .format("int64")
            .description(
                "Secondes avant nouvel essai ; présent sur 429 RATE_LIMITED et sur les 423 de"
                    + " verrouillage d'authentification, avec l'en-tête Retry-After.")
            .example(37));
    props.put(
        "existingLinkId",
        new UUIDSchema()
            .description("Identifiant du lien déjà existant ; présent sur 409 LINK_EXISTS."));
    props.put(
        "allowedLinkTypes",
        new ArraySchema()
            .items(
                new ObjectSchema()
                    .description("Un type de lien autorisé.")
                    .addProperty(
                        "code",
                        new StringSchema()
                            .description("Code du type de lien.")
                            .example("medication_of"))
                    .addProperty(
                        "label",
                        new StringSchema()
                            .description("Libellé du type de lien.")
                            .example("Médicament de")))
            .description(
                "Types de lien autorisés entre les deux entrées, objets {code, label} ; présent"
                    + " sur 422 LINK_TYPE_NOT_ALLOWED."));
    props.put(
        "usedByPublishedProfiles",
        new IntegerSchema()
            .description(
                "Nombre de fiches publiées qui utilisent encore l'entrée de vocabulaire ;"
                    + " présent sur 409 VOCABULARY_IN_USE."));
    props.put(
        "missing",
        new ArraySchema()
            .items(new StringSchema())
            .description(
                "Éléments manquants pour l'action demandée : champs du profil (409"
                    + " PROFILE_INCOMPLETE) ou justificatifs (409 VERIFICATION_INCOMPLETE)."));
    props.put(
        "suggestedEmotion",
        new ObjectSchema()
            .description(
                "Émotion du vocabulaire dont le libellé est identique au tag demandé (forme"
                    + " EmotionView) ; présent sur 422 TAG_IS_EMOTION de POST et PATCH /api/tags.")
            .additionalProperties(true));
    props.put(
        "pendingDocuments",
        new ArraySchema()
            .items(new UUIDSchema())
            .description(
                "Identifiants des justificatifs encore en attente de qualification ; présent sur"
                    + " 409 ADMIN_APPROVAL_INCOMPLETE."));
    return new ObjectSchema()
        .description(
            "Corps de toute réponse d'erreur (application/problem+json, RFC 9457). Les champs"
                + " type, title, status, detail et code sont toujours présents ; errors[] et les"
                + " champs complémentaires (retryAfterSeconds, existingLinkId, allowedLinkTypes,"
                + " missing, pendingDocuments, usedByPublishedProfiles, suggestedEmotion)"
                + " seulement pour les codes indiqués.")
        .properties(props)
        .required(List.of("type", "title", "status", "code"));
  }

  private static void orderTags(OpenAPI openApi) {
    if (openApi.getTags() == null) {
      return;
    }
    List<Tag> tags = new ArrayList<>(openApi.getTags());
    tags.sort(Comparator.comparingInt((Tag t) -> rank(t.getName())).thenComparing(Tag::getName));
    openApi.setTags(tags);
  }

  private static int rank(String name) {
    int index = TAG_ORDER.indexOf(name);
    return index < 0 ? Integer.MAX_VALUE : index;
  }
}
