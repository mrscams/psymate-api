package com.psymate;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.GlobalExceptionHandler;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class ArchitectureTests {

  private static final String ROOT = "com.psymate";
  private static final String FIXTURE_ROOT = "psymatefixture.badadmin";
  private static final String[] CLINICAL_MODULES = {
    "patients", "clinical", "linking", "overview", "search"
  };

  private static final String JPA_FIXTURE_ROOT = "psymatefixture.badjpa";

  private static final String ENTITY = "jakarta.persistence.Entity";
  private static final String TABLE = "jakarta.persistence.Table";
  private static final String COLUMN = "jakarta.persistence.Column";
  private static final String TRANSIENT = "jakarta.persistence.Transient";
  private static final String IMMUTABLE = "org.hibernate.annotations.Immutable";
  private static final String DYNAMIC_UPDATE = "org.hibernate.annotations.DynamicUpdate";
  private static final List<String> JPA_ASSOCIATIONS =
      List.of(
          "jakarta.persistence.ManyToOne",
          "jakarta.persistence.OneToMany",
          "jakarta.persistence.OneToOne",
          "jakarta.persistence.ManyToMany");

  /**
   * Tables sans UPDATE ni DELETE pour psymate_app (REVOKE UPDATE, DELETE) : security_event et
   * patient_version (V01 l. 304), verification_decision et document_access_log (V03 l. 74).
   * L'entite qui les mappe doit etre {@code @Immutable}. Liste figee ici, pas de lecture SQL au
   * runtime : une nouvelle table append-only s'ajoute ici dans la meme PR que sa migration.
   */
  static final Set<String> APPEND_ONLY_TABLES =
      Set.of("security_event", "patient_version", "verification_decision", "document_access_log");

  /**
   * entry_version (V05 l. 132 : REVOKE UPDATE, DELETE ; l. 139 : GRANT UPDATE (title, body,
   * payload), G Q31 / E7 O11). Hibernate emet par defaut un UPDATE de toutes les colonnes, refuse a
   * psymate_app : l'entite doit etre {@code @Immutable} ou {@code @DynamicUpdate}.
   */
  static final String ENTRY_VERSION_TABLE = "entry_version";

  /** Colonne ecrite par trigger (V04, V08) et colonne generee (V05) : jamais ecrites par JPA. */
  static final Set<String> DB_MANAGED_COLUMNS = Set.of("search_vector", "occurred_range");

  private static final JavaClasses PRODUCTION = new ClassFileImporter().importPackages(ROOT);

  /** Module name = first package segment after the root package. */
  private static String moduleOf(JavaClass c, String root) {
    String pkg = c.getPackageName();
    if (!pkg.startsWith(root + ".")) {
      return null;
    }
    String rest = pkg.substring(root.length() + 1);
    int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }

  // allowEmptyShould(true): with zero production classes the rules are vacuously true; each rule
  // is proven non-vacuous by the fixture tests below.
  static ArchRule noRepositoryAccessAcrossModules(String root) {
    return classes()
        .should(
            new ArchCondition<JavaClass>("access a repository of another module") {
              @Override
              public void check(JavaClass origin, ConditionEvents events) {
                String from = moduleOf(origin, root);
                for (Dependency d : origin.getDirectDependenciesFromSelf()) {
                  JavaClass target = d.getTargetClass();
                  String to = moduleOf(target, root);
                  if (from != null
                      && to != null
                      && !from.equals(to)
                      && target.getSimpleName().endsWith("Repository")) {
                    events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                  }
                }
              }
            })
        .allowEmptyShould(true);
  }

  static ArchRule moduleMustNotDependOn(String root, String source, String... forbidden) {
    String[] targets = new String[forbidden.length];
    for (int i = 0; i < forbidden.length; i++) {
      targets[i] = root + "." + forbidden[i] + "..";
    }
    return noClasses()
        .that()
        .resideInAPackage(root + "." + source + "..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(targets)
        .allowEmptyShould(true);
  }

  // ---------------------------------------------------------------------------------------------
  // Gardes JPA (M8-01, G Q32, etude-jpa section 6)
  // ---------------------------------------------------------------------------------------------

  private static boolean isEntity(JavaClass c) {
    return c.isAnnotatedWith(ENTITY);
  }

  private static String toSnakeCase(String name) {
    return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }

  private static String unquote(String identifier) {
    return identifier.replaceAll("[`\"\\[\\]]", "").trim().toLowerCase(Locale.ROOT);
  }

  /** Nom de table : {@code @Table(name)}, sinon nom de classe en snake_case (naming Boot). */
  private static String tableOf(JavaClass entity) {
    String explicit =
        entity
            .tryGetAnnotationOfType(TABLE)
            .flatMap(a -> a.get("name"))
            .map(Object::toString)
            .map(ArchitectureTests::unquote)
            .orElse("");
    return explicit.isEmpty() ? toSnakeCase(entity.getSimpleName()) : explicit;
  }

  private static boolean hasAnnotationInHierarchy(JavaClass entity, String annotation) {
    if (entity.isAnnotatedWith(annotation)) {
      return true;
    }
    return entity.getAllRawSuperclasses().stream().anyMatch(c -> c.isAnnotatedWith(annotation));
  }

  private static void collectTypes(JavaType type, List<JavaClass> out) {
    JavaClass erasure = type.toErasure();
    while (erasure.isArray()) {
      erasure = erasure.getComponentType();
    }
    out.add(erasure);
    if (type instanceof JavaParameterizedType parameterized) {
      for (JavaType argument : parameterized.getActualTypeArguments()) {
        collectTypes(argument, out);
      }
    }
  }

  /** Cible d'une association : {@code targetEntity} si present, sinon type (ou type parametre). */
  private static List<JavaClass> associationTargets(JavaAnnotation<?> association, JavaType type) {
    List<JavaClass> targets = new ArrayList<>();
    Object explicit = association.get("targetEntity").orElse(null);
    if (explicit instanceof JavaClass declared && !declared.getName().equals("void")) {
      targets.add(declared);
    } else {
      collectTypes(type, targets);
    }
    return targets;
  }

  /**
   * Une entite ne porte aucune association JPA vers une entite d'un autre module : les cles
   * etrangeres transverses restent des {@code UUID} nus. Modulith ne voit pas ce couplage (le type
   * cible vit dans un autre package, donc il "passe"), le couplage est pourtant bien reel.
   */
  static ArchRule noJpaAssociationAcrossModules(String root) {
    return classes()
        .that(annotatedWith(ENTITY))
        .should(
            new ArchCondition<JavaClass>("declare a JPA association towards another module") {
              @Override
              public void check(JavaClass entity, ConditionEvents events) {
                String from = moduleOf(entity, root);
                if (from == null) {
                  return;
                }
                for (JavaField field : entity.getAllFields()) {
                  checkMember(entity, field, field.getType(), from, events);
                }
                for (JavaMethod method : entity.getAllMethods()) {
                  checkMember(entity, method, method.getReturnType(), from, events);
                }
              }

              private void checkMember(
                  JavaClass entity,
                  JavaMember member,
                  JavaType type,
                  String from,
                  ConditionEvents events) {
                for (String association : JPA_ASSOCIATIONS) {
                  if (!member.isAnnotatedWith(association)) {
                    continue;
                  }
                  for (JavaClass target :
                      associationTargets(member.getAnnotationOfType(association), type)) {
                    String to = moduleOf(target, root);
                    if (to != null && !to.equals(from)) {
                      events.add(
                          SimpleConditionEvent.violated(
                              member,
                              entity.getName()
                                  + "."
                                  + member.getName()
                                  + " declares @"
                                  + association.substring(association.lastIndexOf('.') + 1)
                                  + " towards "
                                  + target.getName()
                                  + " (module "
                                  + to
                                  + ", entity module "
                                  + from
                                  + ")"));
                    }
                  }
                }
              }
            })
        .allowEmptyShould(true);
  }

  /**
   * Aucune entite dans un DTO ni dans une classe {@code web} (champs, signatures, appels), et
   * aucune entite dans un package {@code api} (qui ne porte que DTO, facades et evenements).
   * Jackson ne doit jamais voir une entite ni un proxy Hibernate.
   */
  static ArchRule noEntityExposedToWebOrDto(String root) {
    DescribedPredicate<JavaClass> web = JavaClass.Predicates.resideInAPackage(root + "..web..");
    DescribedPredicate<JavaClass> api = JavaClass.Predicates.resideInAPackage(root + "..api..");
    DescribedPredicate<JavaClass> dto = JavaClass.Predicates.simpleNameEndingWith("Dto");
    return classes()
        .that(web.or(api).or(dto))
        .should(
            new ArchCondition<JavaClass>("expose a JPA entity (web, Dto) or be an entity (api)") {
              @Override
              public void check(JavaClass origin, ConditionEvents events) {
                if (api.test(origin) && isEntity(origin)) {
                  events.add(
                      SimpleConditionEvent.violated(
                          origin, origin.getName() + " is an @Entity in an api package"));
                }
                if (!web.test(origin) && !dto.test(origin)) {
                  return;
                }
                for (Dependency d : origin.getDirectDependenciesFromSelf()) {
                  JavaClass target = d.getTargetClass();
                  if (!target.equals(origin) && isEntity(target)) {
                    events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                  }
                }
              }
            })
        .allowEmptyShould(true);
  }

  /**
   * Tables append-only (REVOKE UPDATE, DELETE) : {@code @Immutable} obligatoire ; entry_version :
   * {@code @Immutable} ou {@code @DynamicUpdate} (GRANT UPDATE (title, body, payload) seulement).
   * Le nom de table suit {@code @Table(name)}, sinon le nom de classe en snake_case. La regle ne
   * depend pas de la racine ; le parametre garde la symetrie avec les autres regles.
   */
  static ArchRule appendOnlyEntitiesAreImmutable(String root) {
    return classes()
        .that(annotatedWith(ENTITY))
        .should(
            new ArchCondition<JavaClass>("be @Immutable (or @DynamicUpdate for entry_version)") {
              @Override
              public void check(JavaClass entity, ConditionEvents events) {
                if (moduleOf(entity, root) == null) {
                  return;
                }
                String table = tableOf(entity);
                boolean immutable = hasAnnotationInHierarchy(entity, IMMUTABLE);
                if (APPEND_ONLY_TABLES.contains(table) && !immutable) {
                  events.add(
                      SimpleConditionEvent.violated(
                          entity,
                          entity.getName()
                              + " maps append-only table "
                              + table
                              + " and must be @Immutable"));
                }
                if (table.equals(ENTRY_VERSION_TABLE)
                    && !immutable
                    && !hasAnnotationInHierarchy(entity, DYNAMIC_UPDATE)) {
                  events.add(
                      SimpleConditionEvent.violated(
                          entity,
                          entity.getName()
                              + " maps entry_version and must be @Immutable or @DynamicUpdate"));
                }
              }
            })
        .allowEmptyShould(true);
  }

  /**
   * Colonnes ecrites par trigger (search_vector) ou generees (occurred_range) : le champ doit etre
   * {@code @Column(insertable = false, updatable = false)}, sinon l'INSERT ou l'UPDATE JPA echoue.
   */
  static ArchRule triggerAndGeneratedColumnsAreReadOnly(String root) {
    return classes()
        .that(annotatedWith(ENTITY))
        .should(
            new ArchCondition<JavaClass>(
                "map search_vector / occurred_range with insertable = false, updatable = false") {
              @Override
              public void check(JavaClass entity, ConditionEvents events) {
                if (moduleOf(entity, root) == null) {
                  return;
                }
                for (JavaField field : entity.getAllFields()) {
                  if (!field.getModifiers().contains(JavaModifier.STATIC)
                      && !field.isAnnotatedWith(TRANSIENT)) {
                    checkMember(entity, field, field.getName(), events);
                  }
                }
                for (JavaMethod method : entity.getAllMethods()) {
                  if (method.isAnnotatedWith(COLUMN)) {
                    checkMember(entity, method, propertyName(method.getName()), events);
                  }
                }
              }

              private String propertyName(String methodName) {
                String bare = methodName;
                if (bare.startsWith("get") && bare.length() > 3) {
                  bare = bare.substring(3);
                } else if (bare.startsWith("is") && bare.length() > 2) {
                  bare = bare.substring(2);
                }
                return Character.toLowerCase(bare.charAt(0)) + bare.substring(1);
              }

              private void checkMember(
                  JavaClass entity, JavaMember member, String property, ConditionEvents events) {
                JavaAnnotation<?> column =
                    member.isAnnotatedWith(COLUMN) ? member.getAnnotationOfType(COLUMN) : null;
                String explicit =
                    column == null
                        ? ""
                        : column
                            .get("name")
                            .map(Object::toString)
                            .map(ArchitectureTests::unquote)
                            .orElse("");
                String name = explicit.isEmpty() ? toSnakeCase(property) : explicit;
                if (!DB_MANAGED_COLUMNS.contains(name)) {
                  return;
                }
                boolean insertable = column != null && isFalse(column, "insertable");
                boolean updatable = column != null && isFalse(column, "updatable");
                if (!insertable || !updatable) {
                  events.add(
                      SimpleConditionEvent.violated(
                          member,
                          entity.getName()
                              + "."
                              + member.getName()
                              + " maps database-managed column "
                              + name
                              + " and needs @Column(insertable = false, updatable = false)"));
                }
              }

              private boolean isFalse(JavaAnnotation<?> column, String property) {
                return column.get(property).map(v -> Boolean.FALSE.equals(v)).orElse(false);
              }
            })
        .allowEmptyShould(true);
  }

  @Test
  void noRepositoryAccessAcrossModulesInProduction() {
    noRepositoryAccessAcrossModules(ROOT).check(PRODUCTION);
  }

  @Test
  void directoryDoesNotTouchClinicalModules() {
    moduleMustNotDependOn(ROOT, "directory", CLINICAL_MODULES).check(PRODUCTION);
  }

  @Test
  void identityAdminDoesNotTouchClinicalModules() {
    moduleMustNotDependOn(ROOT, "identity.admin", CLINICAL_MODULES).check(PRODUCTION);
  }

  @Test
  void repositoryRuleDetectsCrossModuleAccess() {
    JavaClasses fixtures = new ClassFileImporter().importPackages("psymatefixture.badarch");
    ArchRule rule = noRepositoryAccessAcrossModules("psymatefixture.badarch");
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("AService")
        .hasMessageContaining("BRepository")
        .hasMessageNotContaining("ARepository");
  }

  @Test
  void directoryRuleDetectsForbiddenImport() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    ArchRule rule = moduleMustNotDependOn(FIXTURE_ROOT, "directory", CLINICAL_MODULES);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("DirBad");
  }

  @Test
  void identityAdminRuleDetectsForbiddenImport() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    ArchRule rule = moduleMustNotDependOn(FIXTURE_ROOT, "identity.admin", CLINICAL_MODULES);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("AdminBad");
  }

  @Test
  void reservedPackagesContainNoClassBesidesPackageInfo() {
    for (String reserved : List.of("ai", "companion")) {
      JavaClasses classes = new ClassFileImporter().importPackages(ROOT + "." + reserved);
      assertThat(classes).as(reserved).isNotEmpty();
      assertThat(classes).allMatch(c -> c.getSimpleName().equals("package-info"));
    }
  }

  /**
   * Tout advice de module doit passer strictement avant le filet global de shared (M7-02) : à
   * précédence égale, l'ordre du scan déciderait et le global attraperait tout.
   */
  @Test
  void moduleAdvicesAreOrderedBeforeTheGlobalHandler() {
    JavaClasses advices =
        new ClassFileImporter()
            .importPackages(ROOT)
            .that(annotatedWith(RestControllerAdvice.class));
    assertThat(advices).as("advices").hasSizeGreaterThan(5);
    for (JavaClass advice : advices) {
      if (advice.getName().equals(GlobalExceptionHandler.class.getName())) {
        assertThat(advice.getAnnotationOfType(Order.class).value())
            .as(advice.getName())
            .isEqualTo(Ordered.LOWEST_PRECEDENCE);
        continue;
      }
      assertThat(advice.isAnnotatedWith(Order.class))
          .as(advice.getName() + " porte @Order")
          .isTrue();
      assertThat(advice.getAnnotationOfType(Order.class).value())
          .as(advice.getName())
          .isEqualTo(AdviceOrder.MODULE)
          .isLessThan(Ordered.LOWEST_PRECEDENCE);
    }
  }

  @Test
  void noJpaAssociationAcrossModulesInProduction() {
    noJpaAssociationAcrossModules(ROOT).check(PRODUCTION);
  }

  @Test
  void noEntityExposedToWebOrDtoInProduction() {
    noEntityExposedToWebOrDto(ROOT).check(PRODUCTION);
  }

  @Test
  void appendOnlyEntitiesAreImmutableInProduction() {
    appendOnlyEntitiesAreImmutable(ROOT).check(PRODUCTION);
  }

  @Test
  void triggerAndGeneratedColumnsAreReadOnlyInProduction() {
    triggerAndGeneratedColumnsAreReadOnly(ROOT).check(PRODUCTION);
  }

  @Test
  void jpaAssociationRuleDetectsCrossModuleAssociation() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(JPA_FIXTURE_ROOT);
    ArchRule rule = noJpaAssociationAcrossModules(JPA_FIXTURE_ROOT);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("CrossModuleOrder")
        .hasMessageContaining("CrossModuleCollection")
        .hasMessageContaining("BEntity")
        .hasMessageNotContaining("SameModuleLine");
  }

  @Test
  void entityExposureRuleDetectsEntityInWebDtoAndApi() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(JPA_FIXTURE_ROOT);
    ArchRule rule = noEntityExposedToWebOrDto(JPA_FIXTURE_ROOT);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("LeakyDto")
        .hasMessageContaining("LeakyController")
        .hasMessageContaining("LeakyApiEntity")
        .hasMessageNotContaining("GoodDto")
        .hasMessageNotContaining("GoodController");
  }

  @Test
  void appendOnlyRuleDetectsMutableEntities() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(JPA_FIXTURE_ROOT);
    ArchRule rule = appendOnlyEntitiesAreImmutable(JPA_FIXTURE_ROOT);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("BadSecurityEvent")
        .hasMessageContaining("BadEntryVersionPlain")
        .hasMessageContaining("DocumentAccessLog")
        .hasMessageNotContaining("GoodPatientVersion")
        .hasMessageNotContaining("GoodEntryVersion");
  }

  @Test
  void databaseManagedColumnsRuleDetectsWritableColumns() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(JPA_FIXTURE_ROOT);
    ArchRule rule = triggerAndGeneratedColumnsAreReadOnly(JPA_FIXTURE_ROOT);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("BadSearchVector")
        .hasMessageContaining("BadOccurredRange")
        .hasMessageNotContaining("GoodGeneratedColumns");
  }
}
