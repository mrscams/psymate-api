package com.psymate.clinical.controller;

import com.psymate.clinical.dto.ClinicalRequest;
import com.psymate.clinical.dto.PatchEntryRequest;
import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.FieldProblem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refuse, avant la désérialisation, les clés de premier niveau que le client n'a pas le droit
 * d'envoyer : authorType et submissionId (A11 : lecture seule, provenance fixée par le serveur) et,
 * pour la modification d'entrée, links (M2-02). Les autres clés inconnues restent ignorées (défaut
 * Boot).
 *
 * <p>Jackson 3 uniquement (tools.jackson.*) : le corps est lu en octets, inspecté en arbre, puis
 * rejoué tel quel au convertisseur.
 *
 * <p>Ce contrôle a lieu avant la garde de propriété (il précède la désérialisation, donc le
 * contrôleur) : un praticien non propriétaire qui envoie une clé interdite reçoit 422 plutôt que
 * 403. Rien n'est lu en base ni écrit à ce stade, et la réponse ne révèle rien sur le patient.
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(assignableTypes = {EntryController.class, SessionController.class})
class ForbiddenFieldsAdvice extends RequestBodyAdviceAdapter {

  private final JsonMapper json;

  ForbiddenFieldsAdvice(JsonMapper json) {
    this.json = json;
  }

  @Override
  public boolean supports(
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    return ClinicalRequest.class.isAssignableFrom(parameter.getParameterType());
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
      List<FieldProblem> problems = new ArrayList<>();
      for (String key : List.of("authorType", "submissionId")) {
        if (root.has(key)) {
          problems.add(new FieldProblem(key, "UNKNOWN_FIELD"));
        }
      }
      // M2-02 : les liens se posent à la création ; après coup, par /api/entries/{id}/links.
      if (PatchEntryRequest.class.isAssignableFrom(parameter.getParameterType())
          && root.has("links")) {
        problems.add(new FieldProblem("links", "NOT_SUPPORTED_ON_PATCH"));
      }
      if (!problems.isEmpty()) {
        throw new EntryRequestException(problems);
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
