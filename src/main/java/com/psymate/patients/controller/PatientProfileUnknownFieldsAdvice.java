package com.psymate.patients.controller;

import com.psymate.patients.dto.CreatePatientProfileItemRequest;
import com.psymate.patients.dto.UpdatePatientProfileItemRequest;
import com.psymate.patients.model.PatientException;
import com.psymate.patients.model.PatientFieldCode;
import com.psymate.patients.service.PatientProfileMessages;
import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.ErrorCode;
import com.psymate.shared.problem.FieldProblem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refuse, avant la désérialisation, toute clé de premier niveau inconnue d'un corps du Profil 360
 * (422 VALIDATION_FAILED, code de champ UNKNOWN_FIELD), comme le PATCH de la fiche patient : seules
 * category, title, detail, periodLabel et periodYear sont admises (id, patientId, authorType,
 * archivedAt… sont fixés par le serveur). Sur le modèle de {@code AppointmentForbiddenFieldsAdvice}
 * : corps lu en octets, inspecté en arbre (Jackson 3), puis rejoué tel quel au convertisseur.
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(assignableTypes = PatientProfileController.class)
class PatientProfileUnknownFieldsAdvice extends RequestBodyAdviceAdapter {

  static final Set<String> ACCEPTED =
      Set.of("category", "title", "detail", "periodLabel", "periodYear");

  private final JsonMapper json;
  private final PatientProfileMessages messages;

  PatientProfileUnknownFieldsAdvice(JsonMapper json, PatientProfileMessages messages) {
    this.json = json;
    this.messages = messages;
  }

  @Override
  public boolean supports(
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    Class<?> type = parameter.getParameterType();
    return type == CreatePatientProfileItemRequest.class
        || type == UpdatePatientProfileItemRequest.class;
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
      List<FieldProblem> errors = new ArrayList<>();
      for (String key : root.propertyNames()) {
        if (!ACCEPTED.contains(key)) {
          errors.add(messages.field(new FieldProblem(key, PatientFieldCode.UNKNOWN_FIELD)));
        }
      }
      if (!errors.isEmpty()) {
        throw new PatientException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ErrorCode.VALIDATION_FAILED,
            errors.get(0).message(),
            List.copyOf(errors));
      }
    }
    return new ReplayedMessage(inputMessage.getHeaders(), bytes);
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
