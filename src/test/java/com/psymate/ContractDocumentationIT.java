package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

/**
 * Garde-fou documentaire du contrat (M7-09) : un agent frontend qui ne lit que
 * contract/openapi.yaml doit comprendre chaque opération sans le code. Le test charge le contrat
 * généré et échoue si une opération n'a pas de résumé ou de description, si un paramètre n'est pas
 * décrit, si une réponse d'erreur ne référence pas le schéma {@code ProblemDetail} commun en
 * application/problem+json, si un schéma de composant ou une de ses propriétés n'a pas de
 * description, si un tag n'est pas décrit, ou si la description générale manque. Toute nouvelle
 * opération doit donc arriver documentée.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class ContractDocumentationIT {

  private static final Set<String> HTTP_METHODS =
      Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");

  private static final String PROBLEM_REF = "#/components/schemas/ProblemDetail";

  private static Map<String, Object> contract;

  @Autowired private MockMvc mockMvc;

  @BeforeAll
  static void reset() {
    contract = null;
  }

  @Test
  void generalDescriptionAndTagsAreDocumented() throws Exception {
    Map<String, Object> doc = contract();
    Map<String, Object> info = map(doc.get("info"));
    assertThat(text(info.get("description")))
        .as("info.description : conventions transverses (authentification, erreurs, pagination…)")
        .hasSizeGreaterThan(500)
        .contains("problem+json")
        .contains("UNAUTHENTICATED")
        .contains("RATE_LIMITED")
        .contains("nextCursor");

    List<String> problems = new ArrayList<>();
    List<Object> tags = list(doc.get("tags"));
    assertThat(tags).as("tags déclarés").isNotEmpty();
    for (Object tag : tags) {
      Map<String, Object> t = map(tag);
      String name = text(t.get("name"));
      if (name.isBlank() || name.contains("-controller") || name.equals(name.toLowerCase())) {
        problems.add("tag illisible : « " + name + " »");
      }
      if (text(t.get("description")).isBlank()) {
        problems.add("tag sans description : " + name);
      }
    }
    assertThat(problems).as("tags").isEmpty();
  }

  @Test
  void everyOperationIsDocumented() throws Exception {
    List<String> problems = new ArrayList<>();
    forEachOperation(
        (id, op) -> {
          if (text(op.get("summary")).isBlank()) {
            problems.add(id + " : summary absent");
          }
          if (text(op.get("description")).isBlank()) {
            problems.add(id + " : description absente");
          }
          if (list(op.get("tags")).isEmpty()) {
            problems.add(id + " : tag absent");
          }
          for (Object p : list(op.get("parameters"))) {
            Map<String, Object> param = map(p);
            if (text(param.get("description")).isBlank()) {
              problems.add(id + " : paramètre " + param.get("name") + " sans description");
            }
          }
          Map<String, Object> responses = map(op.get("responses"));
          if (responses.isEmpty()) {
            problems.add(id + " : aucune réponse déclarée");
          }
          responses.forEach(
              (status, r) -> {
                Map<String, Object> response = map(r);
                if (text(response.get("description")).isBlank()) {
                  problems.add(id + " : réponse " + status + " sans description");
                }
                if (status.startsWith("4") || status.startsWith("5")) {
                  Map<String, Object> content = map(response.get("content"));
                  Map<String, Object> problem = map(content.get("application/problem+json"));
                  String ref = text(map(problem.get("schema")).get("$ref"));
                  if (!PROBLEM_REF.equals(ref)) {
                    problems.add(
                        id
                            + " : réponse "
                            + status
                            + " sans schéma application/problem+json → ProblemDetail");
                  }
                }
              });
        });
    assertThat(problems).as("opérations non documentées").isEmpty();
  }

  @Test
  void everySchemaAndPropertyIsDocumented() throws Exception {
    List<String> problems = new ArrayList<>();
    Map<String, Object> schemas = map(map(contract().get("components")).get("schemas"));
    assertThat(schemas).isNotEmpty();
    schemas.forEach(
        (name, s) -> {
          Map<String, Object> schema = map(s);
          if (text(schema.get("description")).isBlank()) {
            problems.add("schéma " + name + " sans description");
          }
          map(schema.get("properties"))
              .forEach(
                  (prop, p) -> {
                    Map<String, Object> property = map(p);
                    // Une propriété qui référence un autre schéma est décrite par ce schéma.
                    if (property.containsKey("$ref")) {
                      return;
                    }
                    if (text(property.get("description")).isBlank()) {
                      problems.add("propriété " + name + "." + prop + " sans description");
                    }
                  });
        });
    Map<String, Object> problem = map(schemas.get("ProblemDetail"));
    assertThat(map(problem.get("properties")).keySet())
        .as("ProblemDetail : champs RFC 9457 + code + errors[]")
        .contains("type", "title", "status", "detail", "instance", "code", "errors");
    assertThat(problems).as("schémas non documentés").isEmpty();
  }

  private interface OperationCheck {
    void check(String id, Map<String, Object> operation);
  }

  private void forEachOperation(OperationCheck check) throws Exception {
    Map<String, Object> paths = map(contract().get("paths"));
    assertThat(paths).isNotEmpty();
    paths.forEach(
        (path, item) ->
            map(item)
                .forEach(
                    (method, op) -> {
                      if (HTTP_METHODS.contains(method)) {
                        check.check(method.toUpperCase() + " " + path, map(op));
                      }
                    }));
  }

  private Map<String, Object> contract() throws Exception {
    if (contract == null) {
      String raw =
          mockMvc
              .perform(get("/v3/api-docs.yaml"))
              .andReturn()
              .getResponse()
              .getContentAsString(StandardCharsets.UTF_8);
      contract = map(new Yaml().load(raw));
    }
    return contract;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object node) {
    return node instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object node) {
    return node instanceof List<?> l ? (List<Object>) l : List.of();
  }

  private static String text(Object node) {
    return node == null ? "" : String.valueOf(node).trim();
  }
}
