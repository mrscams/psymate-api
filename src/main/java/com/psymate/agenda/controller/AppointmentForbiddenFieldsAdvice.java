package com.psymate.agenda.controller;

import com.psymate.agenda.dto.AttachPatientRequest;
import com.psymate.agenda.dto.CancelAppointmentRequest;
import com.psymate.agenda.dto.CreateAppointmentRequest;
import com.psymate.agenda.dto.UpdateAppointmentRequest;
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
 * Refuse, avant la désérialisation, les clés de premier niveau que le client n'a pas le droit
 * d'envoyer à {@link AppointmentController} (422 VALIDATION_FAILED, code de champ UNKNOWN_FIELD) :
 * ce que le serveur fixe seul (status, practitionerId, tenantId, sessionEntryId, et authorType,
 * submissionId comme dans clinical, A11) et, sur un PATCH, ce qui ne se modifie pas (patientId ;
 * tentative, qui passe par /confirm ; recurrence, acceptée à la création seulement depuis M11-07).
 * Les autres clés inconnues restent ignorées (défaut Boot). Sur le modèle de {@code
 * clinical.controller.ForbiddenFieldsAdvice} : corps lu en octets, inspecté en arbre (Jackson 3),
 * puis rejoué tel quel au convertisseur.
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(assignableTypes = AppointmentController.class)
class AppointmentForbiddenFieldsAdvice extends RequestBodyAdviceAdapter {

  /** Champs fixés par le serveur, refusés dans tout corps d'écriture. */
  static final List<String> SERVER_FIELDS =
      List.of(
          "authorType", "submissionId", "status", "practitionerId", "tenantId", "sessionEntryId");

  /**
   * Refusés à la modification en plus : une occurrence se déplace seule, la répétition ne se change
   * pas après coup (G Q55).
   */
  static final List<String> UPDATE_FIELDS = List.of("patientId", "tentative", "recurrence");

  private final JsonMapper json;
  private final AgendaMessages messages;

  AppointmentForbiddenFieldsAdvice(JsonMapper json, AgendaMessages messages) {
    this.json = json;
    this.messages = messages;
  }

  @Override
  public boolean supports(
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    Class<?> type = parameter.getParameterType();
    return type == CreateAppointmentRequest.class
        || type == UpdateAppointmentRequest.class
        || type == AttachPatientRequest.class
        || type == CancelAppointmentRequest.class;
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
      List<String> forbidden = new ArrayList<>(SERVER_FIELDS);
      if (parameter.getParameterType() == UpdateAppointmentRequest.class) {
        forbidden.addAll(UPDATE_FIELDS);
      }
      List<String> present = forbidden.stream().filter(root::has).toList();
      if (!present.isEmpty()) {
        throw unknownFields(present);
      }
    }
    return new ReplayedMessage(inputMessage.getHeaders(), bytes);
  }

  /** 422 VALIDATION_FAILED, rendu par le handler commun des {@link ProblemException}. */
  private ProblemException unknownFields(List<String> fields) {
    Locale locale = locale();
    List<FieldProblem> errors =
        fields.stream()
            .map(f -> new FieldProblem(f, "UNKNOWN_FIELD"))
            .map(p -> new FieldProblem(p.field(), p.code(), messages.message(p, locale)))
            .toList();
    return new ProblemException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "VALIDATION_FAILED",
        messages.get("agenda.error.validation.title", locale),
        messages.get("agenda.error.validation.detail", locale),
        errors);
  }

  private Locale locale() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return messages.localeOf(attrs.getRequest());
    }
    return Locale.FRENCH;
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
