package com.psymate.billing.controller;

import com.psymate.billing.dto.BillingProfileRequest;
import com.psymate.billing.dto.CreateBillingServiceRequest;
import com.psymate.billing.dto.UpdateBillingServiceRequest;
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
 *   <li>un booléen doit être un booléen JSON, un texte une chaîne JSON (INVALID_FORMAT).
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
    BOOLEAN
  }

  /** Champs d'une prestation (création et modification). */
  private static final Map<String, Kind> SERVICE_FIELDS =
      Map.of(
          "label", Kind.TEXT,
          "durationMin", Kind.INTEGER,
          "price", Kind.INTEGER,
          "isDefault", Kind.BOOLEAN);

  /** Champs du profil de facturation d'un patient. */
  private static final Map<String, Kind> PROFILE_FIELDS =
      Map.of(
          "rate", Kind.INTEGER,
          "addressLines", Kind.TEXT,
          "thirdPartyName", Kind.TEXT,
          "thirdPartyAddress", Kind.TEXT);

  /** Champs admis, et leur type JSON, par type de corps. */
  static final Map<Class<?>, Map<String, Kind>> FIELDS =
      Map.of(
          CreateBillingServiceRequest.class, SERVICE_FIELDS,
          UpdateBillingServiceRequest.class, SERVICE_FIELDS,
          BillingProfileRequest.class, PROFILE_FIELDS);

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
  static List<FieldProblem> problems(JsonNode root, Map<String, Kind> fields) {
    List<FieldProblem> problems = new ArrayList<>();
    for (Map.Entry<String, JsonNode> property : root.properties()) {
      String name = property.getKey();
      JsonNode value = property.getValue();
      Kind kind = fields.get(name);
      if (kind == null) {
        problems.add(new FieldProblem(name, "UNKNOWN_FIELD"));
      } else if (!value.isNull()) {
        String code = check(kind, value);
        if (code != null) {
          problems.add(new FieldProblem(name, code));
        }
      }
    }
    return problems;
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
