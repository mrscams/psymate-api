package com.psymate.billing.controller;

import com.psymate.billing.dto.BillingIbanDeleteRequest;
import com.psymate.billing.dto.BillingIbanRequest;
import com.psymate.billing.dto.BillingProfileRequest;
import com.psymate.billing.dto.BillingSettingsRequest;
import com.psymate.billing.dto.CreateBillingServiceRequest;
import com.psymate.billing.dto.CreateInvoiceBatchRequest;
import com.psymate.billing.dto.CreateInvoiceRequest;
import com.psymate.billing.dto.UpdateBillableSessionRequest;
import com.psymate.billing.dto.UpdateBillingServiceRequest;
import com.psymate.billing.dto.UpdateInvoiceRequest;
import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrôle, avant la désérialisation, la forme des corps d'écriture de la facturation (422
 * VALIDATION_FAILED avec errors[] par champ) :
 *
 * <ul>
 *   <li>toute clé de premier niveau hors des champs de la requête est refusée (UNKNOWN_FIELD, L5 :
 *       « champs inconnus refusés ») : praticien, cabinet, identifiants et toute autre clé ;
 *   <li>un montant ou une durée doit être un entier JSON (G Q84 : centimes entiers) : {@code 60.5},
 *       {@code 60.0} ou {@code "6000"} sont refusés (INVALID_FORMAT), au lieu d'être tronqués ou
 *       convertis par Jackson ({@code ACCEPT_FLOAT_AS_INT} et la conversion des chaînes sont actifs
 *       par défaut) ; un entier hors de l'intervalle d'un {@code int} est OUT_OF_RANGE ;
 *   <li>un booléen doit être un booléen JSON, un texte une chaîne JSON (INVALID_FORMAT) ;
 *   <li>un bloc (réglages : {@code vat}, {@code numbering}, {@code payment}, {@code
 *       emailTemplates}) doit être un objet JSON, contrôlé de la même façon, champ par champ
 *       ({@code vat.rateBp}) ; une liste de textes ({@code payment.acceptedModes}) un tableau de
 *       chaînes ;
 *   <li>une liste d'objets (lignes d'une facture : {@code lines}) doit être un tableau d'objets,
 *       chacun contrôlé champ par champ ({@code lines[2].unitPrice}) ; un élément null est
 *       REQUIRED, un élément qui n'est pas un objet INVALID_FORMAT. Un prix unitaire peut être
 *       négatif (remise) mais jamais décimal.
 * </ul>
 *
 * Une valeur {@code null} passe : sa règle est celle de la requête (obligatoire, efface…). Le
 * contrôle reste local aux contrôleurs de la facturation : la configuration de Jackson de
 * l'application n'est pas touchée. Sur le modèle de {@code AppointmentForbiddenFieldsAdvice} :
 * corps lu en octets, inspecté en arbre (Jackson 3), puis rejoué tel quel au convertisseur.
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(basePackageClasses = BillingBodyAdvice.class)
class BillingBodyAdvice extends RequestBodyAdviceAdapter {

  /** Type JSON attendu d'un champ de requête. */
  enum Kind {
    TEXT,
    INTEGER,
    BOOLEAN,
    TEXT_LIST,
    OBJECT,
    OBJECT_LIST
  }

  /**
   * Champ de requête : son type JSON et, pour un bloc ({@link Kind#OBJECT}) ou une liste d'objets
   * ({@link Kind#OBJECT_LIST}), les champs de chaque objet.
   */
  record Field(Kind kind, Map<String, Field> fields) {

    static final Field TEXT = new Field(Kind.TEXT, Map.of());
    static final Field INTEGER = new Field(Kind.INTEGER, Map.of());
    static final Field BOOLEAN = new Field(Kind.BOOLEAN, Map.of());
    static final Field TEXT_LIST = new Field(Kind.TEXT_LIST, Map.of());

    static Field object(Map<String, Field> fields) {
      return new Field(Kind.OBJECT, fields);
    }

    static Field objectList(Map<String, Field> fields) {
      return new Field(Kind.OBJECT_LIST, fields);
    }
  }

  /** Champs d'une prestation (création et modification). */
  private static final Map<String, Field> SERVICE_FIELDS =
      Map.of(
          "label", Field.TEXT,
          "durationMin", Field.INTEGER,
          "price", Field.INTEGER,
          "isDefault", Field.BOOLEAN);

  /** Champs du profil de facturation d'un patient. */
  private static final Map<String, Field> PROFILE_FIELDS =
      Map.of(
          "rate", Field.INTEGER,
          "addressLines", Field.TEXT,
          "thirdPartyName", Field.TEXT,
          "thirdPartyAddress", Field.TEXT);

  /** Champs d'un modèle d'e-mail des réglages. */
  private static final Field TEMPLATE =
      Field.object(Map.of("subject", Field.TEXT, "body", Field.TEXT));

  /** Champs modifiables des réglages de facturation (M15-06). */
  private static final Map<String, Field> SETTINGS_FIELDS =
      Map.of(
          "siret", Field.TEXT,
          "addressLines", Field.TEXT,
          "displayEmail", Field.TEXT,
          "displayPhone", Field.TEXT,
          "vat",
              Field.object(
                  Map.of("regime", Field.TEXT, "rateBp", Field.INTEGER, "vatNumber", Field.TEXT)),
          "numbering",
              Field.object(Map.of("invoicePrefix", Field.TEXT, "creditNotePrefix", Field.TEXT)),
          "payment",
              Field.object(
                  Map.of(
                      "dueTerm", Field.TEXT,
                      "chequePayee", Field.TEXT,
                      "acceptedModes", Field.TEXT_LIST,
                      "showBankDetails", Field.BOOLEAN)),
          "footerNote", Field.TEXT,
          "emailTemplates", Field.object(Map.of("invoice", TEMPLATE, "reminder", TEMPLATE)));

  /** Champs d'une ligne de facture (M15-08) : unitPrice entier, négatif permis. */
  private static final Field INVOICE_LINES =
      Field.objectList(
          Map.of(
              "sessionEntryId", Field.TEXT,
              "serviceId", Field.TEXT,
              "label", Field.TEXT,
              "occurredOn", Field.TEXT,
              "quantity", Field.INTEGER,
              "unitPrice", Field.INTEGER));

  /** Champs modifiables d'un brouillon de facture (PATCH, M15-08). */
  private static final Map<String, Field> UPDATE_INVOICE_FIELDS =
      Map.of(
          "patientId", Field.TEXT,
          "recipient", Field.object(Map.of("name", Field.TEXT, "addressLines", Field.TEXT)),
          "issueDate", Field.TEXT,
          "dueTerm", Field.TEXT,
          "dueOn", Field.TEXT,
          "footerNote", Field.TEXT,
          "lines", INVOICE_LINES);

  /** Champs admis, et leur type JSON, par type de corps. */
  static final Map<Class<?>, Map<String, Field>> FIELDS =
      Map.ofEntries(
          Map.entry(CreateBillingServiceRequest.class, SERVICE_FIELDS),
          Map.entry(UpdateBillingServiceRequest.class, SERVICE_FIELDS),
          Map.entry(BillingProfileRequest.class, PROFILE_FIELDS),
          Map.entry(BillingSettingsRequest.class, SETTINGS_FIELDS),
          Map.entry(
              BillingIbanRequest.class, Map.of("iban", Field.TEXT, "currentPassword", Field.TEXT)),
          Map.entry(BillingIbanDeleteRequest.class, Map.of("currentPassword", Field.TEXT)),
          Map.entry(
              UpdateBillableSessionRequest.class,
              Map.of("amount", Field.INTEGER, "serviceId", Field.TEXT)),
          Map.entry(
              CreateInvoiceRequest.class,
              Map.of(
                  "patientId", Field.TEXT,
                  "sessionEntryIds", Field.TEXT_LIST,
                  "lines", INVOICE_LINES)),
          Map.entry(CreateInvoiceBatchRequest.class, Map.of("sessionEntryIds", Field.TEXT_LIST)),
          Map.entry(UpdateInvoiceRequest.class, UPDATE_INVOICE_FIELDS));

  private final JsonMapper json;
  private final BillingMessages messages;

  BillingBodyAdvice(JsonMapper json, BillingMessages messages) {
    this.json = json;
    this.messages = messages;
  }

  @Override
  public boolean supports(
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    return FIELDS.containsKey(parameter.getParameterType());
  }

  @Override
  public HttpInputMessage beforeBodyRead(
      HttpInputMessage inputMessage,
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType)
      throws IOException {
    byte[] bytes = inputMessage.getBody().readAllBytes();
    JsonNode root = parse(bytes);
    if (root != null && root.isObject()) {
      List<FieldProblem> problems = problems(root, FIELDS.get(parameter.getParameterType()));
      if (!problems.isEmpty()) {
        throw invalid(problems);
      }
    }
    return new ReplayedMessage(inputMessage.getHeaders(), bytes);
  }

  /** Problèmes de forme d'un corps, dans l'ordre de ses clés. */
  static List<FieldProblem> problems(JsonNode root, Map<String, Field> fields) {
    List<FieldProblem> problems = new ArrayList<>();
    collect(root, fields, "", problems);
    return problems;
  }

  /** Problèmes d'un objet JSON, champs nommés {@code prefix + nom} ({@code vat.rateBp}). */
  private static void collect(
      JsonNode object, Map<String, Field> fields, String prefix, List<FieldProblem> problems) {
    for (Map.Entry<String, JsonNode> property : object.properties()) {
      String name = prefix + property.getKey();
      JsonNode value = property.getValue();
      Field field = fields.get(property.getKey());
      if (field == null) {
        problems.add(new FieldProblem(name, "UNKNOWN_FIELD"));
      } else if (value.isNull()) {
        continue;
      } else if (field.kind() == Kind.OBJECT) {
        if (value.isObject()) {
          collect(value, field.fields(), name + ".", problems);
        } else {
          problems.add(new FieldProblem(name, "INVALID_FORMAT"));
        }
      } else if (field.kind() == Kind.OBJECT_LIST) {
        if (!value.isArray()) {
          problems.add(new FieldProblem(name, "INVALID_FORMAT"));
          continue;
        }
        for (int i = 0; i < value.size(); i++) {
          JsonNode element = value.get(i);
          String elementName = name + "[" + i + "]";
          if (element.isNull()) {
            problems.add(new FieldProblem(elementName, "REQUIRED"));
          } else if (element.isObject()) {
            collect(element, field.fields(), elementName + ".", problems);
          } else {
            problems.add(new FieldProblem(elementName, "INVALID_FORMAT"));
          }
        }
      } else {
        String code = check(field.kind(), value);
        if (code != null) {
          problems.add(new FieldProblem(name, code));
        }
      }
    }
  }

  /** Code d'erreur d'une valeur non nulle, ou null si elle a le type attendu. */
  static String check(Kind kind, JsonNode value) {
    return switch (kind) {
      case TEXT -> value.isString() ? null : "INVALID_FORMAT";
      case BOOLEAN -> value.isBoolean() ? null : "INVALID_FORMAT";
      case INTEGER -> {
        if (!value.isIntegralNumber()) {
          yield "INVALID_FORMAT";
        }
        yield value.canConvertToInt() ? null : "OUT_OF_RANGE";
      }
      case TEXT_LIST -> {
        if (!value.isArray()) {
          yield "INVALID_FORMAT";
        }
        for (JsonNode element : value) {
          if (!element.isString()) {
            yield "INVALID_FORMAT";
          }
        }
        yield null;
      }
      case OBJECT -> value.isObject() ? null : "INVALID_FORMAT";
      case OBJECT_LIST -> value.isArray() ? null : "INVALID_FORMAT";
    };
  }

  /** 422 VALIDATION_FAILED, rendu par le handler commun des {@link ProblemException}. */
  private ProblemException invalid(List<FieldProblem> problems) {
    Locale locale = locale();
    List<FieldProblem> errors =
        problems.stream()
            .map(p -> new FieldProblem(p.field(), p.code(), messages.message(p, locale)))
            .toList();
    return new ProblemException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "VALIDATION_FAILED",
        messages.get("billing.error.validation.title", locale),
        messages.get("billing.error.validation.detail", locale),
        errors);
  }

  private Locale locale() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return messages.localeOf(attrs.getRequest());
    }
    return ServedLanguage.FR.locale();
  }

  /** Un JSON illisible est laissé au convertisseur, qui produit l'erreur de syntaxe habituelle. */
  private JsonNode parse(byte[] bytes) {
    try {
      return json.readTree(bytes);
    } catch (JacksonException e) {
      return null;
    }
  }

  private record ReplayedMessage(HttpHeaders headers, byte[] body) implements HttpInputMessage {

    @Override
    public InputStream getBody() {
      return new ByteArrayInputStream(body);
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }
  }
}
