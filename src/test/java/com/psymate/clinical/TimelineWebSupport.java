package com.psymate.clinical;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Socle des tests de timeline : une séance ouverte et des constructeurs d'entrées à un instant
 * précis (par l'API, jamais en SQL).
 */
abstract class TimelineWebSupport extends ClinicalWebSupport {

  protected String session;

  @BeforeEach
  void openSession() throws Exception {
    session = startSession(patient);
  }

  protected String timelineUrl(String query) {
    return "/api/patients/" + patient + "/timeline" + (query.isEmpty() ? "" : "?" + query);
  }

  protected String summaryUrl(String query) {
    return "/api/patients/" + patient + "/timeline/summary" + (query.isEmpty() ? "" : "?" + query);
  }

  /** Crée une entrée de ce type à cet instant ; {@code extra} est un fragment JSON optionnel. */
  protected String entry(String type, String at, String extra) throws Exception {
    return createEntry(
        patient,
        "{\"type\":\""
            + type
            + "\",\"occurred\":{\"from\":\""
            + at
            + "\",\"precision\":\"exact\"}"
            + (extra == null || extra.isEmpty() ? "" : "," + extra)
            + "}");
  }

  protected String note(String at) throws Exception {
    return note(at, null);
  }

  protected String note(String at, String extra) throws Exception {
    String body = "\"body\":\"Une note\"";
    return entry("note", at, extra == null ? body : body + "," + extra);
  }

  protected String feeling(String at, String emotion, int intensity) throws Exception {
    return feeling(at, emotion, intensity, null);
  }

  protected String feeling(String at, String emotion, int intensity, String extra)
      throws Exception {
    String detail =
        "\"feeling\":{\"emotionCode\":\"" + emotion + "\",\"intensity\":" + intensity + "}";
    return entry("feeling", at, extra == null ? detail : detail + "," + extra);
  }

  protected String event(String at, String title, String extra) throws Exception {
    String t = "\"title\":\"" + title + "\"";
    return entry("event", at, extra == null ? t : t + "," + extra);
  }

  /** Situation sans fin si {@code to} est nul. */
  protected String situation(String from, String to, String title, String extra) throws Exception {
    String t = "\"title\":\"" + title + "\"";
    String json =
        "{\"type\":\"situation\","
            + t
            + ",\"occurred\":{\"from\":\""
            + from
            + "\""
            + (to == null ? "" : ",\"to\":\"" + to + "\"")
            + ",\"precision\":\"exact\"}"
            + (extra == null ? "" : "," + extra)
            + "}";
    return createEntry(patient, json);
  }

  protected static List<String> read(ResultActions result, String path) throws Exception {
    return JsonPath.read(body(result), path);
  }
}
