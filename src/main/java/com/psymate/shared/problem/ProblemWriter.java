package com.psymate.shared.problem;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/**
 * Écrit un {@link ProblemDetail} en application/problem+json UTF-8 directement dans la réponse
 * servlet, pour les endroits qui n'ont pas de handler MVC : filtres de limitation de débit, entry
 * point 401 et access denied handler 403 de Spring Security. La sérialisation est faite ici
 * (Jackson 3, {@code tools.jackson}) plutôt que par les convertisseurs de Spring, qui n'existent
 * pas encore à ce niveau de la chaîne ; le corps est identique à celui que produiraient les
 * handlers : {@code type}, {@code title}, {@code status}, {@code detail}, {@code instance} (si
 * posé), puis les propriétés ({@code code}, {@code errors}…).
 */
public final class ProblemWriter {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private ProblemWriter() {}

  public static void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(toJson(problem));
  }

  static String toJson(ProblemDetail problem) {
    Map<String, Object> body = new LinkedHashMap<>();
    // Spring 7 : type absent = about:blank ; les filtres l'écrivent toujours, comme avant M7-02.
    body.put("type", problem.getType() == null ? "about:blank" : problem.getType().toString());
    body.put("title", problem.getTitle());
    body.put("status", problem.getStatus());
    body.put("detail", problem.getDetail());
    if (problem.getInstance() != null) {
      body.put("instance", problem.getInstance().toString());
    }
    if (problem.getProperties() != null) {
      body.putAll(problem.getProperties());
    }
    return MAPPER.writeValueAsString(body);
  }
}
