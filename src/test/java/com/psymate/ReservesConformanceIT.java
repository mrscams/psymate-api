package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.clinical.api.EntryArchived;
import com.psymate.clinical.api.EntryCorrected;
import com.psymate.clinical.api.EntryCreated;
import com.psymate.clinical.application.CorrectEntryCommand;
import com.psymate.clinical.application.CreateEntryCommand;
import com.psymate.clinical.application.EntryService;
import com.psymate.clinical.domain.EntryType;
import com.psymate.clinical.domain.SessionDetail;
import com.psymate.clinical.domain.Visibility;
import com.psymate.clinical.export.PatientExportService;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.linking.api.LinkAccepted;
import com.psymate.linking.api.LinkRejected;
import com.psymate.linking.model.Link;
import com.psymate.linking.service.LinkService;
import com.psymate.patients.PatientTestSupport;
import com.psymate.patients.api.PatientCreated;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

/**
 * M7-05 : vérification des hooks V2 (A4.9, A8) et des réserves du compagnon patient (A11, R13, C7).
 * Chaque méthode correspond à un point du rapport de conformité imprimé en fin de classe ; les
 * points qui dépendent d'une tâche pas encore livrée sont {@code @Disabled} avec la tâche qui les
 * réactive.
 *
 * <p>La règle de grâce est neutralisée ({@code versioning.grace-period=0s}) pour que la correction
 * crée une version et émette EntryCorrected (A4.8 : pas d'événement pendant la grâce).
 */
@SpringBootTest(properties = "versioning.grace-period=0s")
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, ReservesConformanceIT.ListenerConfig.class})
@ExtendWith(ReservesConformanceIT.ConformanceReport.class)
class ReservesConformanceIT {

  private static final Path CONTRACT = Path.of("contract", "openapi.yaml");

  /**
   * Abonnés transactionnels : le registre Modulith n'écrit une publication que s'il existe au moins
   * un abonné pour l'événement.
   */
  static class OutboxListeners {
    @ApplicationModuleListener
    void on(PatientCreated event) {}

    @ApplicationModuleListener
    void on(EntryCreated event) {}

    @ApplicationModuleListener
    void on(EntryCorrected event) {}

    @ApplicationModuleListener
    void on(EntryArchived event) {}

    @ApplicationModuleListener
    void on(LinkAccepted event) {}

    @ApplicationModuleListener
    void on(LinkRejected event) {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ListenerConfig {
    @Bean
    OutboxListeners outboxListeners() {
      return new OutboxListeners();
    }
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationContext context;
  @Autowired private EntryService entries;
  @Autowired private PatientExportService exports;
  @Autowired private LinkService linkService;
  @Autowired private DomainEventPublisher publisher;
  @Autowired private TransactionTemplate tx;

  private UUID tenant;
  private UUID practitioner;
  private UUID patient;
  private UUID session;
  private UUID note;
  private UUID archivedNote;

  @BeforeEach
  void seed() throws InterruptedException {
    tenant = UUID.randomUUID();
    practitioner = UUID.randomUUID();
    String email = practitioner + "@example.test";
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet conformité')", tenant);
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist')",
        practitioner,
        tenant,
        email,
        email);
    CurrentPractitioner principal =
        new CurrentPractitioner(practitioner, tenant, email, "Dr Test", "psychologist", "none");
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

    // Chemin applicatif réel : PatientCreated, puis séance d'accueil créée par l'abonné
    // (asynchrone).
    patient = PatientTestSupport.createPatient(context, "Patient conformité");
    awaitIntake();

    session =
        entries.create(
            patient,
            new CreateEntryCommand(
                EntryType.SESSION,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new SessionDetail(null, "in_person", 50, null)));
    note = entries.create(patient, note("Première version"));
    entries.correct(
        note,
        new CorrectEntryCommand(
            null, "Seconde version", null, null, null, null, null, null, "précision"));
    archivedNote = entries.create(patient, note("À archiver"));
    entries.archive(archivedNote);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  private CreateEntryCommand note(String body) {
    return new CreateEntryCommand(
        EntryType.NOTE, session, null, body, null, null, null, null, null, null);
  }

  private void awaitIntake() throws InterruptedException {
    for (int i = 0; i < 100 && intakeCount() == 0; i++) {
      Thread.sleep(100);
    }
    assertThat(intakeCount()).as("séance d'accueil créée après PatientCreated").isEqualTo(1);
  }

  private int intakeCount() {
    return count(
        "select count(*) from entry e join entry_session s on s.entry_id = e.id"
            + " where e.patient_id = ? and s.kind = 'intake'",
        patient);
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  /** Publications du registre pour notre abonné, pour un type d'événement et un identifiant. */
  private int publications(Class<?> eventType, UUID subjectId) {
    return count(
        "select count(*) from event_publication where listener_id like '%ReservesConformanceIT%'"
            + " and event_type = ? and serialized_event::text like ?",
        eventType.getName(), "%" + subjectId + "%");
  }

  private boolean tableExists(String table) {
    return count(
            "select count(*) from information_schema.tables"
                + " where table_schema = current_schema() and table_name = ?",
            table)
        == 1;
  }

  // ---- 1. outbox (A4.9)

  @Test
  void outboxContainsPatientEntryAndCorrectionEvents() {
    assertThat(publications(PatientCreated.class, patient)).as("PatientCreated").isEqualTo(1);
    assertThat(publications(EntryCreated.class, note)).as("EntryCreated").isEqualTo(1);
    assertThat(publications(EntryCorrected.class, note)).as("EntryCorrected").isEqualTo(1);
    assertThat(publications(EntryArchived.class, archivedNote)).as("EntryArchived").isEqualTo(1);
    // Une entrée jamais corrigée n'a pas d'EntryCorrected.
    assertThat(publications(EntryCorrected.class, archivedNote)).isZero();
  }

  /**
   * A4.9 : LinkAccepted est publié par LinkService à la création d'un lien. LinkRejected n'est
   * jamais émis en V1 (le statut rejected est réservé aux suggestions de l'IA, V2) : on vérifie que
   * la suppression d'un lien n'en publie pas, puis que le crochet de l'outbox est prêt en publiant
   * un LinkRejected à la main dans une transaction.
   */
  @Test
  void outboxContainsLinkEvents() {
    UUID event =
        entries.create(
            patient,
            new CreateEntryCommand(
                EntryType.EVENT,
                session,
                "Décès du père",
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    UUID note = entries.create(patient, note("Une autre note"));
    Link accepted = linkService.create(event, note, "follows", null);
    Link removed = linkService.create(note, event, "follows", null);
    linkService.remove(removed.id());

    assertThat(publications(LinkAccepted.class, accepted.id())).as("LinkAccepted").isEqualTo(1);
    assertThat(publications(LinkAccepted.class, removed.id()))
        .as("LinkAccepted du lien supprimé")
        .isEqualTo(1);
    assertThat(publications(LinkRejected.class, removed.id()))
        .as("la suppression d'un lien n'émet pas LinkRejected")
        .isZero();

    UUID suggestion = UUID.randomUUID();
    tx.executeWithoutResult(
        status ->
            publisher.publish(
                new LinkRejected(tenant, suggestion, patient, event, note, "follows")));
    assertThat(publications(LinkRejected.class, suggestion)).as("LinkRejected").isEqualTo(1);
  }

  // ---- 2. tables V2 et compagnon vides (A4.9, A8, A11)

  @Test
  void v2TablesExistAndAreEmpty() {
    for (String table : List.of("entry_embedding", "entry_chunk")) {
      assertThat(tableExists(table)).as(table + " existe").isTrue();
      assertThat(count("select count(*) from " + table)).as(table + " est vide").isZero();
    }
  }

  @Test
  void companionTablesExistAndAreEmpty() {
    for (String table : List.of("patient_device", "patient_submission")) {
      assertThat(tableExists(table)).as(table + " existe").isTrue();
      assertThat(count("select count(*) from " + table)).as(table + " est vide").isZero();
    }
  }

  @Test
  void aiFeedbackTableExistsAndIsEmpty() {
    assertThat(tableExists("ai_feedback")).as("ai_feedback existe").isTrue();
    assertThat(count("select count(*) from ai_feedback")).as("ai_feedback est vide").isZero();
  }

  // ---- 3 et 4. réserves du compagnon (A11, R13)

  @Test
  void everyEntryIsAuthoredByPractitionerWithoutSubmission() {
    assertThat(count("select count(*) from entry")).as("des entrées existent").isGreaterThan(0);
    assertThat(
            count(
                "select count(*) from entry"
                    + " where author_type <> 'practitioner' or submission_id is not null"))
        .as("entrées non rédigées par le praticien ou rattachées à un lot")
        .isZero();
  }

  @Test
  void entrySessionRequiredConstraintExists() {
    assertThat(
            count(
                "select count(*) from pg_constraint c join pg_class t on t.oid = c.conrelid"
                    + " where t.relname = 'entry' and c.contype = 'c'"
                    + " and c.conname = 'ck_entry_session_required'"))
        .isEqualTo(1);
  }

  // ---- 5. contrat (R13 : authorType en lecture seulement)

  @Test
  @SuppressWarnings("unchecked")
  void contractExposesAuthorTypeOnlyInResponses() throws IOException {
    Map<String, Object> root;
    try (Reader reader = Files.newBufferedReader(CONTRACT, StandardCharsets.UTF_8)) {
      root = new Yaml().load(reader);
    }
    Map<String, Object> schemas =
        (Map<String, Object>) ((Map<String, Object>) root.get("components")).get("schemas");

    Map<String, Object> entryDto = (Map<String, Object>) schemas.get("EntryDto");
    assertThat(entryDto).as("components.schemas.EntryDto").isNotNull();
    assertThat((Map<String, Object>) entryDto.get("properties"))
        .as("EntryDto.properties")
        .containsKey("authorType");

    Set<String> requestSchemas = new LinkedHashSet<>();
    Map<String, Object> paths = (Map<String, Object>) root.get("paths");
    for (Object pathItem : paths.values()) {
      for (Object operation : ((Map<String, Object>) pathItem).values()) {
        if (operation instanceof Map<?, ?> op && op.get("requestBody") instanceof Map<?, ?> body) {
          collectRefs(body, schemas, requestSchemas);
        }
      }
    }
    assertThat(requestSchemas).as("schémas de corps de requête").isNotEmpty();
    List<String> exposing = new ArrayList<>();
    for (String name : requestSchemas) {
      Map<String, Object> schema = (Map<String, Object>) schemas.get(name);
      assertThat(schema)
          .as("schéma référencé " + name + " absent de components.schemas")
          .isNotNull();
      Object properties = schema.get("properties");
      if (properties instanceof Map<?, ?> p && p.containsKey("authorType")) {
        exposing.add(name);
      }
    }
    assertThat(exposing).as("authorType ne doit jamais être accepté en écriture").isEmpty();
  }

  /** Parcourt un nœud YAML et ajoute chaque schéma référencé (récursivement) à {@code found}. */
  private static void collectRefs(Object node, Map<String, Object> schemas, Set<String> found) {
    if (node instanceof Map<?, ?> map) {
      Object ref = map.get("$ref");
      if (ref instanceof String s && s.startsWith("#/components/schemas/")) {
        String name = s.substring("#/components/schemas/".length());
        if (found.add(name) && schemas.get(name) != null) {
          collectRefs(schemas.get(name), schemas, found);
        }
      }
      for (Object value : map.values()) {
        collectRefs(value, schemas, found);
      }
    } else if (node instanceof Collection<?> items) {
      for (Object item : items) {
        collectRefs(item, schemas, found);
      }
    }
  }

  // ---- 6. packages réservés (A11, C7)

  @Test
  void reservedPackagesContainOnlyPackageInfo() throws IOException {
    for (String pkg : List.of("ai", "companion")) {
      JavaClasses classes = new ClassFileImporter().importPackages("com.psymate." + pkg);
      List<String> names = classes.stream().map(JavaClass::getSimpleName).toList();
      // isNotEmpty : le package doit exister (sinon allMatch serait vrai à vide).
      assertThat(names)
          .as("classes compilées de com.psymate." + pkg)
          .isNotEmpty()
          .allMatch("package-info"::equals);

      Path sources = Path.of("src", "main", "java", "com", "psymate", pkg);
      try (Stream<Path> files = Files.walk(sources)) {
        assertThat(files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()))
            .as("sources de " + sources)
            .containsExactly("package-info.java");
      }
    }
  }

  // ---- 7. export (A4.9 : visibility = private exclu des exports)

  /**
   * M6-05 (A4.9) : l'export Markdown du patient contient les entrées cliniques (dernière version)
   * et NI l'entrée privée, NI son titre ou son corps, NI aucune version antérieure d'une entrée
   * passée de clinical à private par correction.
   */
  @Test
  void privateEntriesAreExcludedFromExport() {
    UUID privateNote =
        entries.create(
            patient,
            new CreateEntryCommand(
                EntryType.NOTE,
                session,
                null,
                "Note privée",
                Visibility.PRIVATE,
                null,
                null,
                null,
                null,
                null));
    assertThat(
            count(
                "select count(*) from entry where id = ? and visibility = 'private'", privateNote))
        .isEqualTo(1);
    UUID clinical =
        entries.create(
            patient,
            new CreateEntryCommand(
                EntryType.NOTE,
                session,
                "Titre clinique export",
                "Corps clinique export",
                null,
                null,
                null,
                null,
                null,
                null));
    UUID flipped =
        entries.create(
            patient,
            new CreateEntryCommand(
                EntryType.NOTE,
                session,
                "Titre bascule v1",
                "Corps bascule v1",
                null,
                null,
                null,
                null,
                null,
                null));

    String before = exports.markdown(patient);
    assertThat(before).contains("Titre clinique export").contains("Corps clinique export");
    assertThat(before).contains("Seconde version").doesNotContain("Première version");
    assertThat(before).contains("Titre bascule v1");
    assertThat(before).doesNotContain("Note privée").doesNotContain("À archiver");

    // Passée à private par correction : la version 1 (grâce neutralisée) disparaît aussi.
    entries.correct(
        flipped,
        new CorrectEntryCommand(
            "Titre bascule v2", null, Visibility.PRIVATE, null, null, null, null, null, "bascule"));
    assertThat(count("select count(*) from entry_version where entry_id = ?", flipped))
        .isEqualTo(2);

    String after = exports.markdown(patient);
    assertThat(after).contains("Titre clinique export");
    assertThat(after)
        .doesNotContain("Note privée")
        .doesNotContain("Titre bascule v1")
        .doesNotContain("Titre bascule v2")
        .doesNotContain("Corps bascule v1");
    assertThat(clinical).isNotNull();
  }

  // ---- rapport de conformité

  /** Collecte l'état de chaque point et imprime le rapport en fin de classe. */
  static class ConformanceReport implements TestWatcher, AfterAllCallback {

    private static final Map<String, String> STATES = new LinkedHashMap<>();

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
      LABELS.put(
          "outboxContainsPatientEntryAndCorrectionEvents",
          "1.  Outbox : PatientCreated, EntryCreated, EntryCorrected, EntryArchived");
      LABELS.put("outboxContainsLinkEvents", "1b. Outbox : LinkAccepted, LinkRejected");
      LABELS.put("v2TablesExistAndAreEmpty", "2a. Tables V2 vides : entry_embedding, entry_chunk");
      LABELS.put("aiFeedbackTableExistsAndIsEmpty", "2b. Table V2 vide : ai_feedback");
      LABELS.put(
          "companionTablesExistAndAreEmpty",
          "2c. Tables compagnon vides : patient_device, patient_submission");
      LABELS.put(
          "everyEntryIsAuthoredByPractitionerWithoutSubmission",
          "3.  entry : author_type = 'practitioner' et submission_id NULL partout");
      LABELS.put(
          "entrySessionRequiredConstraintExists", "4.  Contrainte ck_entry_session_required");
      LABELS.put(
          "contractExposesAuthorTypeOnlyInResponses",
          "5.  Contrat : authorType en réponse seulement");
      LABELS.put("reservedPackagesContainOnlyPackageInfo", "6.  Packages ai et companion vides");
      LABELS.put("privateEntriesAreExcludedFromExport", "7.  Export : visibility=private exclu");
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
      STATES.put(context.getRequiredTestMethod().getName(), "OK");
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
      STATES.put(context.getRequiredTestMethod().getName(), "KO");
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
      STATES.put(context.getRequiredTestMethod().getName(), "KO (abandonné)");
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
      String task = reason.map(r -> r.split(" :")[0]).orElse("?");
      STATES.put(context.getRequiredTestMethod().getName(), "DISABLED jusqu'à " + task);
    }

    @Override
    public void afterAll(ExtensionContext context) throws IOException {
      StringBuilder report = new StringBuilder();
      report.append("\n=== Conformité V2 / compagnon (M7-05) ===\n");
      LABELS.forEach(
          (method, label) ->
              report.append(
                  String.format(
                      "%-78s %s%n", label, STATES.getOrDefault(method, "KO (non exécuté)"))));
      report.append("=========================================\n");
      System.out.print(report);
      Path target = Path.of("target");
      if (Files.isDirectory(target)) {
        Files.writeString(target.resolve("conformite-v2-compagnon.txt"), report);
      }
    }
  }
}
